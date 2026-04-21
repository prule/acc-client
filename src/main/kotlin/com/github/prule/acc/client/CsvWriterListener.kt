package com.github.prule.acc.client

import com.github.doyaaaaaken.kotlincsv.client.CsvFileWriter
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import org.slf4j.LoggerFactory

@OptIn(KotlinCsvExperimental::class)
class CsvWriterListener<T>(
    directory: Path?,
) : MessageListener<T> {
  private val logger = LoggerFactory.getLogger(javaClass)
  private lateinit var writer: CsvFileWriter

  init {
    if (directory != null) {
      val targetDir = directory.toFile()
      targetDir.mkdirs()
      val filename = "simulator-recording-${dateToFilename()}.csv"
      writer = csvWriter().openAndGetRawWriter(File(targetDir, filename))
      writer.writeRow("date", "type", "hex", "json")
      logger.debug("Writing $filename")
    } else {
      logger.debug("Csv Writer NOT enabled")
    }
  }

  override fun onStop() {
    writer.close()
  }

  private fun dateToFilename(): String = LocalDateTime.now().toString().replace(":", "-")

  override fun onMessage(
      bytes: ByteArray,
      message: T,
      messageSender: MessageSender,
  ) {
    writer.writeRow(
        listOf(
            LocalDateTime.now(),
            bytes[0].toInt(),
            bytes.toHexString(),
            JsonFormatter.toJsonString(message as Any),
        ),
    )
  }
}
