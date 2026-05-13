package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound
import io.kaitai.struct.ByteBufferKaitaiStream
import io.mockk.mockk
import io.mockk.verify
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Drives [SessionDetector] using real recorded UDP frames from the simulator playback CSV. Parsing
 * via the real [AccBroadcastingInbound] sidesteps Kaitai-generated nested type names that are hard
 * to reference in mocks.
 */
class SessionDetectorTest {

  // Bytes lifted from src/main/resources/.../playback-events.csv
  private val realtimeUpdateSessionHex =
    "02000000000a0504988b49f00bae480d00000008004472697661626c650700436f636b706974090042617369632" +
      "048554400ba9657471e26000000d96b01000500000003d15b0000bc9f00004b70000000010000"
  private val trackDataHex =
    "05120000000d005265642042756c6c2052696e671a000000de1000000708004472697661626c650705004368617" +
      "365080046617243686173650600426f6e6e657407004461736850726f0700436f636b706974040044617368060" +
      "048656c6d6574070048656c6963616d01070048656c6963616d07004f6e626f6172640408004f6e626f6172643" +
      "008004f6e626f6172643108004f6e626f6172643208004f6e626f6172643307007069746c616e65020c007069" +
      "746c616e655f43616d310c007069746c616e655f43616d320400736574310d0900536574315f43616d3309005" +
      "36574315f43616d340900536574315f43616d350900536574315f43616d360900536574315f43616d3709005" +
      "36574315f43616d390a00536574315f43616d31300a00536574315f43616d31310a00536574315f43616d3132" +
      "0a00536574315f43616d31330d00536574315f43616d31345f31340900536574315f43616d310900536574315" +
      "f43616d320400736574320a0900536574325f43616d330900536574325f43616d340900536574325f43616d35" +
      "0900536574325f43616d360900536574325f43616d370900536574325f43616d380900536574325f43616d390" +
      "a00536574325f43616d31300900536574325f43616d310900536574325f43616d32050073657456520b09004" +
      "3616d657261565231090043616d657261565232090043616d657261565233090043616d657261565234090043" +
      "616d657261565235090043616d657261565236090043616d657261565237090043616d6572615652380900436" +
      "16d6572615652390a0043616d657261565231300a0043616d65726156523131060500426c616e6b0900426173" +
      "696320485544040048656c70090054696d655461626c650c0042726f616463617374696e670800547261636b4d" +
      "6170"
  private val entryListCarHex =
    "060000010c00426c61636b2046616c636f6e04000000000002000104004c756361050053746f6c7a030053544f01" +
      "0200"

  private fun parse(hex: String): Pair<ByteArray, AccBroadcastingInbound> {
    val bytes = hex.hexToByteArray()
    val msg =
      AccBroadcastingInbound(
        ByteBufferKaitaiStream(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
      )
    return bytes to msg
  }

  @Test
  fun `defers session start when preamble not ready then fires when track + car arrive`() {
    val context = ClientContext()
    val updater = ContextUpdater(context)
    val listener: SessionEventListener = mockk(relaxed = true)
    val detector = SessionDetector(context, listOf(listener))
    val sender: MessageSender = mockk(relaxed = true)

    // Phase enters SESSION but no preamble yet → must defer.
    val (rtBytes, rtMsg) = parse(realtimeUpdateSessionHex)
    detector.onMessage(rtBytes, rtMsg, sender)
    verify(exactly = 0) { listener.onSessionStart(any()) }

    // Track data arrives — still missing cars.
    val (tdBytes, tdMsg) = parse(trackDataHex)
    updater.onMessage(tdBytes, tdMsg, sender)
    detector.onMessage(tdBytes, tdMsg, sender)
    verify(exactly = 0) { listener.onSessionStart(any()) }

    // First car arrives — preamble now ready → deferred start fires.
    val (carBytes, carMsg) = parse(entryListCarHex)
    updater.onMessage(carBytes, carMsg, sender)
    detector.onMessage(carBytes, carMsg, sender)
    verify(exactly = 1) { listener.onSessionStart(any()) }
  }

  @Test
  fun `fires onSessionStart immediately when preamble already ready`() {
    val context = ClientContext()
    val updater = ContextUpdater(context)
    val listener: SessionEventListener = mockk(relaxed = true)
    val detector = SessionDetector(context, listOf(listener))
    val sender: MessageSender = mockk(relaxed = true)

    // Pre-populate preamble.
    val (tdBytes, tdMsg) = parse(trackDataHex)
    updater.onMessage(tdBytes, tdMsg, sender)
    val (carBytes, carMsg) = parse(entryListCarHex)
    updater.onMessage(carBytes, carMsg, sender)

    val (rtBytes, rtMsg) = parse(realtimeUpdateSessionHex)
    detector.onMessage(rtBytes, rtMsg, sender)

    verify(exactly = 1) {
      listener.onSessionStart(match { it.track.name == "Red Bull Ring" && it.cars.containsKey(0) })
    }
  }

  @Test
  fun `forwards messages to listeners only while session active`() {
    val context = ClientContext()
    val updater = ContextUpdater(context)
    val listener: SessionEventListener = mockk(relaxed = true)
    val detector = SessionDetector(context, listOf(listener))
    val sender: MessageSender = mockk(relaxed = true)

    // Preamble messages BEFORE session starts → no forward.
    val (tdBytes, tdMsg) = parse(trackDataHex)
    updater.onMessage(tdBytes, tdMsg, sender)
    detector.onMessage(tdBytes, tdMsg, sender)
    val (carBytes, carMsg) = parse(entryListCarHex)
    updater.onMessage(carBytes, carMsg, sender)
    detector.onMessage(carBytes, carMsg, sender)
    verify(exactly = 0) { listener.onSessionMessage(any(), any(), any()) }

    // Phase=SESSION → start, and the realtime msg itself is forwarded.
    val (rtBytes, rtMsg) = parse(realtimeUpdateSessionHex)
    detector.onMessage(rtBytes, rtMsg, sender)
    verify(exactly = 1) { listener.onSessionMessage(rtBytes, rtMsg, sender) }
  }

  @Test
  fun `onStop fires onSessionStop when session active`() {
    val context = ClientContext()
    val updater = ContextUpdater(context)
    val listener: SessionEventListener = mockk(relaxed = true)
    val detector = SessionDetector(context, listOf(listener))
    val sender: MessageSender = mockk(relaxed = true)

    val (tdBytes, tdMsg) = parse(trackDataHex)
    updater.onMessage(tdBytes, tdMsg, sender)
    val (carBytes, carMsg) = parse(entryListCarHex)
    updater.onMessage(carBytes, carMsg, sender)
    val (rtBytes, rtMsg) = parse(realtimeUpdateSessionHex)
    detector.onMessage(rtBytes, rtMsg, sender)

    detector.onStop()
    verify(exactly = 1) { listener.onSessionStop() }
  }

  @Test
  fun `onStop does nothing when session not active`() {
    val listener: SessionEventListener = mockk(relaxed = true)
    val detector = SessionDetector(ClientContext(), listOf(listener))

    detector.onStop()
    verify(exactly = 0) { listener.onSessionStop() }
  }

  @Test
  fun `context survives across simulated reconnect`() {
    val context = ClientContext()
    val updater = ContextUpdater(context)
    val sender: MessageSender = mockk(relaxed = true)

    val (tdBytes, tdMsg) = parse(trackDataHex)
    updater.onMessage(tdBytes, tdMsg, sender)
    val (carBytes, carMsg) = parse(entryListCarHex)
    updater.onMessage(carBytes, carMsg, sender)

    assertThat(context.track?.name).isEqualTo("Red Bull Ring")
    assertThat(context.cars).containsKey(0)

    // Simulate reconnect: detector stops, new updater observes new connection but same context.
    val detector = SessionDetector(context, listOf(mockk(relaxed = true)))
    detector.onStop()

    // Context preserved — new connection can resume without re-fetching preamble.
    assertThat(context.track?.name).isEqualTo("Red Bull Ring")
    assertThat(context.cars).containsKey(0)
  }
}
