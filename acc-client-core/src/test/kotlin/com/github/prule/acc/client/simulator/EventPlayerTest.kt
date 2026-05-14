package com.github.prule.acc.client.simulator

import com.github.prule.acc.client.MessageSender
import com.github.prule.acc.messages.AccBroadcastingInbound
import io.kaitai.struct.ByteBufferKaitaiStream
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventPlayerTest {

  /**
   * Plays back a single REALTIME_UPDATE, then asks the player to emit a session-over frame. Asserts
   * the emitted frame parses as a valid `RealtimeUpdate` with phase=`SESSION_OVER` while all other
   * fields are preserved.
   */
  @Test
  fun `emitSessionOver rewrites the phase byte on the last realtime update`(): Unit = runBlocking {
    val payload = realtimeUpdateBytes(phase = 5, focusedCarIndex = 7)

    val sender = mockk<MessageSender>(relaxed = true)
    val player = EventPlayer(MultiRowSource(listOf(payload)), millisDelay = 0)
    player.sendPackets(this, sender).join()
    player.emitSessionOver()

    val captured = mutableListOf<ByteArray>()
    verify { sender.send(capture(captured)) }
    assertThat(captured).hasSize(2) // playback emission + session-over emission

    val finalFrame = captured.last()
    assertThat(finalFrame[EventPlayer.PHASE_BYTE_OFFSET]).isEqualTo(EventPlayer.SESSION_OVER_PHASE)
    val expected =
      payload.copyOf().also { it[EventPlayer.PHASE_BYTE_OFFSET] = EventPlayer.SESSION_OVER_PHASE }
    assertThat(finalFrame).isEqualTo(expected)

    val parsed = AccBroadcastingInbound(ByteBufferKaitaiStream(finalFrame))
    val body = parsed.body() as AccBroadcastingInbound.RealtimeUpdate
    assertThat(body.phase()).isEqualTo(AccBroadcastingInbound.SessionPhase.SESSION_OVER)
    assertThat(body.focusedCarIndex()).isEqualTo(7)
  }

  /**
   * When a new client registers while a previous playback is still running, the player should: (1)
   * emit a `SESSION_OVER` frame to the old client, (2) cancel the previous playback job, (3) start
   * a fresh playback that delivers events to the new client (not the old one).
   */
  @Test
  fun `new client triggers session restart for previous client`(): Unit = runBlocking {
    val rt = realtimeUpdateBytes(phase = 5, focusedCarIndex = 7)
    val rows = List(100) { rt } // long enough that playback is still active when we re-register
    val player = EventPlayer(MultiRowSource(rows), millisDelay = 50)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
      val firstSender = mockk<MessageSender>(relaxed = true)
      val firstJob = player.sendPackets(scope, firstSender)

      // Wait until the first playback has emitted at least one realtime update so emitSessionOver
      // has something to base its frame on.
      withTimeout(2_000) {
        while (true) {
          val captured = mutableListOf<ByteArray>()
          verify(atLeast = 0) { firstSender.send(capture(captured)) }
          if (captured.isNotEmpty()) break
          delay(10)
        }
      }

      val secondSender = mockk<MessageSender>(relaxed = true)
      player.sendPackets(scope, secondSender)

      // The previous playback must be cancelled.
      withTimeout(2_000) { while (firstJob.isActive) delay(10) }
      assertThat(firstJob.isCancelled).isTrue()

      // The previous client must receive a SESSION_OVER frame (last send before cancellation).
      val firstCaptured = mutableListOf<ByteArray>()
      verify { firstSender.send(capture(firstCaptured)) }
      val sessionOver = firstCaptured.lastOrNull {
        it[EventPlayer.PHASE_BYTE_OFFSET] == EventPlayer.SESSION_OVER_PHASE
      }
      assertThat(sessionOver).isNotNull()

      // The new client must start receiving events.
      withTimeout(2_000) {
        while (true) {
          val captured = mutableListOf<ByteArray>()
          verify(atLeast = 0) { secondSender.send(capture(captured)) }
          if (captured.isNotEmpty()) break
          delay(10)
        }
      }
    } finally {
      scope.cancel()
    }
  }

  @Test
  fun `emitSessionOver is a no-op when no realtime update has been sent`() {
    val player = EventPlayer(MultiRowSource(emptyList()), millisDelay = 0)
    player.emitSessionOver() // sender never registered → silent no-op (no crash)
  }

  @Test
  fun `by default all car updates are forwarded`(): Unit = runBlocking {
    val rt = realtimeUpdateBytes(phase = 5, focusedCarIndex = 7)
    val focusedCar = realtimeCarUpdateBytes(carIndex = 7)
    val otherCar = realtimeCarUpdateBytes(carIndex = 99)
    val sender = mockk<MessageSender>(relaxed = true)
    val player = EventPlayer(MultiRowSource(listOf(rt, focusedCar, otherCar)), millisDelay = 0)

    player.sendPackets(this, sender).join()

    val captured = mutableListOf<ByteArray>()
    verify { sender.send(capture(captured)) }
    assertThat(captured).hasSize(3) // realtime_update + both car updates
  }

  @Test
  fun `onlyPlayerEvents drops car updates for non-focused cars`(): Unit = runBlocking {
    val rt = realtimeUpdateBytes(phase = 5, focusedCarIndex = 7)
    val focusedCar = realtimeCarUpdateBytes(carIndex = 7)
    val otherCar = realtimeCarUpdateBytes(carIndex = 99)
    val sender = mockk<MessageSender>(relaxed = true)
    val player =
      EventPlayer(
        MultiRowSource(listOf(rt, focusedCar, otherCar)),
        millisDelay = 0,
        onlyPlayerEvents = true,
      )

    player.sendPackets(this, sender).join()

    val captured = mutableListOf<ByteArray>()
    verify { sender.send(capture(captured)) }
    assertThat(captured).hasSize(2) // realtime_update + focused-car update; non-focused dropped
    assertThat(captured.map { it[0].toUByte().toInt() })
      .containsExactly(
        AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE.id().toInt(),
        AccBroadcastingInbound.InboundMsgType.REALTIME_CAR_UPDATE.id().toInt(),
      )
  }

  // --- helpers ---

  private class MultiRowSource(private val rows: List<ByteArray>) : Source {
    override fun inputStream(): java.io.InputStream {
      val sb = StringBuilder("date,type,hex,json\n")
      rows.forEach { bytes ->
        sb.append("2026-01-01T00:00:00.000,${bytes[0].toUByte().toInt()},${toHex(bytes)},{}\n")
      }
      return sb.toString().byteInputStream()
    }

    companion object {
      private fun toHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    }
  }

  /** Build a syntactically valid REALTIME_UPDATE frame with the given phase byte + focused car. */
  private fun realtimeUpdateBytes(phase: Int, focusedCarIndex: Int): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE.id().toInt())
    out.writeU2(0) // event_index
    out.writeU2(0) // session_index
    out.write(4) // session_type = race
    out.write(phase) // phase
    out.writeF4(0f) // session_time_ms
    out.writeF4(0f) // session_end_time_ms
    out.writeS4(focusedCarIndex)
    out.writeAccString("")
    out.writeAccString("")
    out.writeAccString("")
    out.write(0) // is_replay_playing → skip optional fields
    out.writeF4(0f) // time_of_day_seconds
    repeat(5) { out.write(0) } // temps + clouds/rain/wetness
    out.writeU4(0) // best_session_lap.laptime_ms
    out.writeU2(0) // best_session_lap.car_index
    out.writeU2(0) // best_session_lap.driver_index
    repeat(5) { out.write(0) } // splits length + 4 flag bytes
    return out.toByteArray()
  }

  private fun ByteArrayOutputStream.writeU2(v: Int) =
    write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())

  private fun ByteArrayOutputStream.writeU4(v: Int) =
    write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())

  private fun ByteArrayOutputStream.writeS4(v: Int) = writeU4(v)

  private fun ByteArrayOutputStream.writeF4(v: Float) =
    write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(v).array())

  private fun ByteArrayOutputStream.writeAccString(s: String) {
    val bytes = s.toByteArray(Charsets.UTF_8)
    writeU2(bytes.size)
    write(bytes)
  }

  /** Build a valid REALTIME_CAR_UPDATE frame for [carIndex]. All numeric fields zeroed. */
  private fun realtimeCarUpdateBytes(carIndex: Int): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(AccBroadcastingInbound.InboundMsgType.REALTIME_CAR_UPDATE.id().toInt())
    out.writeU2(carIndex) // car_index
    out.writeU2(0) // driver_index
    out.write(0) // driver_count
    out.write(0) // gear (s1)
    out.writeF4(0f) // world_pos_x
    out.writeF4(0f) // world_pos_y
    out.writeF4(0f) // yaw
    out.write(0) // car_location
    out.writeU2(0) // kmh
    out.writeU2(0) // position
    out.writeU2(0) // cup_position
    out.writeU2(0) // track_position
    out.writeF4(0f) // spline_position
    out.writeU2(0) // laps
    out.writeS4(0) // delta
    writeLapInfo(out)
    writeLapInfo(out)
    writeLapInfo(out)
    return out.toByteArray()
  }

  /** lap_time_ms(s4) + car_index(u2) + driver_index(u2) + num_splits(u1)=0 + 4 flag bytes */
  private fun writeLapInfo(out: ByteArrayOutputStream) {
    out.writeS4(0)
    out.writeU2(0)
    out.writeU2(0)
    out.write(0) // num_splits = 0, so no splits array follows
    out.write(0)
    out.write(0)
    out.write(0)
    out.write(0)
  }
}
