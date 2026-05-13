package com.github.prule.acc.client

import com.github.doyaaaaaken.kotlincsv.client.CsvFileWriter
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.github.prule.acc.messages.AccBroadcastingInbound
import io.kaitai.struct.ByteBufferKaitaiStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.time.LocalDateTime
import org.slf4j.LoggerFactory

/**
 * Records each session to its own CSV file. On session start, writes the preamble (track data,
 * entry list, car entries) before any session messages so the file can be replayed by
 * [com.github.prule.acc.client.simulator.AccSimulator].
 *
 * Re-parses preamble bytes for the JSON column rather than holding parsed messages in
 * [SessionPreamble], keeping the snapshot lightweight.
 */
@OptIn(KotlinCsvExperimental::class)
class RecordingSessionListener(private val directory: Path?) : SessionEventListener {
  private val logger = LoggerFactory.getLogger(javaClass)
  private var writer: CsvFileWriter? = null

  override fun onSessionStart(preamble: SessionPreamble) {
    if (directory == null) return
    directory.toFile().mkdirs()
    val filename = "simulator-recording-${dateToFilename()}.csv"
    val file = File(directory.toFile(), filename)
    logger.debug("Recording session to {}", file)
    writer = csvWriter().openAndGetRawWriter(file)
    writer?.writeRow("date", "type", "hex", "json")

    preamble.raw.trackData?.let { writeRawRow(it) }
    preamble.raw.entryList?.let { writeRawRow(it) }
    // Preserve carId order so replays are deterministic.
    preamble.raw.carEntries.toSortedMap().values.forEach { writeRawRow(it) }
  }

  override fun onSessionMessage(
    bytes: ByteArray,
    message: AccBroadcastingInbound,
    sender: MessageSender,
  ) {
    writeParsedRow(bytes, message)
  }

  override fun onSessionStop() {
    writer?.close()
    writer = null
    logger.debug("Session recording closed")
  }

  private fun writeRawRow(bytes: ByteArray) {
    val parsed =
      runCatching {
          AccBroadcastingInbound(
            ByteBufferKaitaiStream(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN))
          )
        }
        .getOrNull()
    writeRow(bytes, parsed)
  }

  private fun writeParsedRow(bytes: ByteArray, message: AccBroadcastingInbound) {
    writeRow(bytes, message)
  }

  private fun writeRow(bytes: ByteArray, parsed: AccBroadcastingInbound?) {
    try {
      writer?.writeRow(
        listOf(
          LocalDateTime.now(),
          bytes[0].toInt(),
          bytes.toHexString(),
          if (parsed != null) JsonFormatter.toJsonString(parsed) else "",
        )
      )
    } catch (e: Exception) {
      logger.error("Error writing message to CSV", e)
    }
  }

  private fun dateToFilename(): String = LocalDateTime.now().toString().replace(":", "-")
}
