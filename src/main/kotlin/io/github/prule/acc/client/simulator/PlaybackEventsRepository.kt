package io.github.prule.acc.client.simulator

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import io.blackmo18.kotlin.grass.core.DateTimeTypes
import io.blackmo18.kotlin.grass.date.time.Java8DateTime
import io.blackmo18.kotlin.grass.dsl.grass
import io.github.prule.acc.client.EventRow
import org.slf4j.LoggerFactory
import java.io.File

class PlaybackEventsRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    @OptIn(ExperimentalStdlibApi::class)
    fun load(file: File): List<EventRow> {
        logger.info("Loading ${file.absolutePath}")
        val csvContents = csvReader().readAllWithHeader(file)
        return grass<EventRow>().harvest(csvContents)
    }
}

@OptIn(ExperimentalStdlibApi::class)
val grass =
    grass<DateTimeTypes> {
        dateFormat = "yyyy-MM-ddTHH:mm:ss.SSS"
        timeFormat = "HH:mm:ss"
        dateTimeSeparator = "/"
        customDataTypes = arrayListOf(Java8DateTime)
    }
