package com.github.prule.acc.client.simulator

import com.github.prule.acc.client.MessageSender
import com.github.prule.acc.messages.AccBroadcastingInbound
import io.kaitai.struct.ByteBufferKaitaiStream
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
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
    val player = EventPlayer(SingleRowSource(payload), millisDelay = 0)
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

  @Test
  fun `emitSessionOver is a no-op when no realtime update has been sent`() {
    val player = EventPlayer(SingleRowSource(ByteArray(0)), millisDelay = 0)
    player.emitSessionOver() // sender never registered → silent no-op (no crash)
  }

  // --- helpers ---

  private class SingleRowSource(private val bytes: ByteArray) : Source {
    override fun inputStream() =
      if (bytes.isEmpty()) "date,type,hex,json\n".byteInputStream()
      else
        "date,type,hex,json\n2026-01-01T00:00:00.000,${bytes[0].toUByte().toInt()},${toHex(bytes)},{}\n"
          .byteInputStream()

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
}
