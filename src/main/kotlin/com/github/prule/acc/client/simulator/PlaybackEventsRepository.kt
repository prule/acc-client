package com.github.prule.acc.client.simulator

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.prule.acc.client.EventRow
import io.blackmo18.kotlin.grass.core.DateTimeTypes
import io.blackmo18.kotlin.grass.date.time.Java8DateTime
import io.blackmo18.kotlin.grass.dsl.grass
import org.slf4j.LoggerFactory

class PlaybackEventsRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    @OptIn(ExperimentalStdlibApi::class)
    fun load(file: Source): List<EventRow> {
        file.inputStream().use { reader ->
            val csvContents = csvReader().readAllWithHeader(reader)
            return grass<EventRow>().harvest(csvContents)
        }
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
