package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound
import io.kaitai.struct.KaitaiStruct
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextUpdaterTest {

  private val context = ClientContext()
  private val updater = ContextUpdater(context)
  private val sender: MessageSender = mockk(relaxed = true)
  private val bytes = byteArrayOf(1, 2, 3)

  private fun inbound(
    type: AccBroadcastingInbound.InboundMsgType,
    body: KaitaiStruct,
  ): AccBroadcastingInbound {
    val m = mockk<AccBroadcastingInbound>()
    every { m.msgType() } returns type
    every { m.body() } returns body
    return m
  }

  @Test
  fun `registration sets connectionId and requests entry list + track data`() {
    val body = mockk<AccBroadcastingInbound.RegistrationResult>()
    every { body.connectionId() } returns 7

    updater.onMessage(
      bytes,
      inbound(AccBroadcastingInbound.InboundMsgType.REGISTRATION_RESULT, body),
      sender,
    )

    assertThat(context.connectionId).isEqualTo(7)
    verify(exactly = 2) { sender.send(any()) }
  }

  @Test
  fun `realtime update sets focusedCarIndex`() {
    val body = mockk<AccBroadcastingInbound.RealtimeUpdate>()
    every { body.focusedCarIndex() } returns 13

    updater.onMessage(
      bytes,
      inbound(AccBroadcastingInbound.InboundMsgType.REALTIME_UPDATE, body),
      sender,
    )

    assertThat(context.focusedCarIndex).isEqualTo(13)
  }

  @Test
  fun `track data caches track + raw bytes`() {
    updater.onMessage(
      byteArrayOf(5, 1, 2),
      inbound(AccBroadcastingInbound.InboundMsgType.TRACK_DATA, trackData("Spa")),
      sender,
    )

    assertThat(context.track?.name).isEqualTo("Spa")
    assertThat(context.rawTrackData).isEqualTo(byteArrayOf(5, 1, 2))
    assertThat(context.lastPreambleAt).isNotNull
  }

  @Test
  fun `track change clears cars`() {
    context.cars[1] = sampleCar(1)
    context.rawCarEntries[1] = byteArrayOf(0)

    updater.onMessage(
      bytes,
      inbound(AccBroadcastingInbound.InboundMsgType.TRACK_DATA, trackData("Spa")),
      sender,
    )
    val versionAfterFirst = context.entryListVersion

    // Same track again — cars retained.
    updater.onMessage(
      bytes,
      inbound(AccBroadcastingInbound.InboundMsgType.TRACK_DATA, trackData("Spa")),
      sender,
    )
    assertThat(context.cars).containsKey(1)
    assertThat(context.entryListVersion).isEqualTo(versionAfterFirst)

    // Different track — cars cleared, version bumped.
    updater.onMessage(
      bytes,
      inbound(AccBroadcastingInbound.InboundMsgType.TRACK_DATA, trackData("Monza")),
      sender,
    )
    assertThat(context.cars).isEmpty()
    assertThat(context.rawCarEntries).isEmpty()
    assertThat(context.entryListVersion).isGreaterThan(versionAfterFirst)
    assertThat(context.track?.name).isEqualTo("Monza")
  }

  @Test
  fun `entry list evicts cars not in new list`() {
    // Seed cars 0 and 99 — only 0 is in the real entry list bytes below.
    context.cars[0] = sampleCar(0)
    context.cars[99] = sampleCar(99)
    context.rawCarEntries[0] = byteArrayOf(0)
    context.rawCarEntries[99] = byteArrayOf(0)

    // Real ENTRY_LIST bytes from playback-events.csv — 25 cars, ids 0..24.
    val entryListHex =
      "0412000000190000000400020005000100070006000a00080009000c000f0010000e0003001300180012000b" +
        "0014001700150016000d001100"
    val entryBytes = entryListHex.hexToByteArray()
    val msg =
      com.github.prule.acc.messages.AccBroadcastingInbound(
        io.kaitai.struct.ByteBufferKaitaiStream(
          java.nio.ByteBuffer.wrap(entryBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        )
      )

    updater.onMessage(entryBytes, msg, sender)

    // 0 is in the list → kept. 99 is not → evicted.
    assertThat(context.cars.keys).contains(0).doesNotContain(99)
    assertThat(context.rawCarEntries.keys).contains(0).doesNotContain(99)
    assertThat(context.entryListVersion).isGreaterThanOrEqualTo(1)
  }

  @Test
  fun `entry list car upserts and stores raw bytes`() {
    val car = mockk<AccBroadcastingInbound.EntryListCar>(relaxed = true)
    every { car.carId() } returns 99

    updater.onMessage(
      byteArrayOf(7, 8),
      inbound(AccBroadcastingInbound.InboundMsgType.ENTRY_LIST_CAR, car),
      sender,
    )

    assertThat(context.cars).containsKey(99)
    assertThat(context.cars[99]?.carId).isEqualTo(99)
    assertThat(context.rawCarEntries[99]).isEqualTo(byteArrayOf(7, 8))
  }

  @Test
  fun `snapshotRawPreamble returns deep copies`() {
    context.rawTrackData = byteArrayOf(1, 2)
    context.rawEntryList = byteArrayOf(3, 4)
    context.rawCarEntries[1] = byteArrayOf(5, 6)

    val snap = context.snapshotRawPreamble()
    context.rawTrackData!![0] = 99
    context.rawCarEntries[1]!![0] = 99

    assertThat(snap.trackData).isEqualTo(byteArrayOf(1, 2))
    assertThat(snap.carEntries[1]).isEqualTo(byteArrayOf(5, 6))
  }

  /**
   * Builds a relaxed mock so reflective decoding paths (cameraSets / hudPages) return defaults
   * without ceremony, while the direct `trackName().data()` call returns the supplied name.
   */
  private fun trackData(name: String): AccBroadcastingInbound.TrackData {
    val td = mockk<AccBroadcastingInbound.TrackData>(relaxed = true)
    every { td.trackName().data() } returns name
    return td
  }

  private fun sampleCar(id: Int): CarEntry =
    CarEntry(
      carId = id,
      carModelType = 0,
      teamName = "",
      raceNumber = 0,
      cupCategory = "",
      currentDriverIndex = 0,
      nationality = 0,
      drivers = emptyList(),
    )
}
