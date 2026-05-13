package com.github.prule.acc.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ProgressReporterTest {

  private lateinit var appender: ListAppender<ILoggingEvent>
  private lateinit var logger: Logger

  @BeforeEach
  fun setUp() {
    logger = LoggerFactory.getLogger(ProgressReporter::class.java) as Logger
    appender = ListAppender()
    appender.start()
    logger.addAppender(appender)
    logger.level = Level.INFO
  }

  @AfterEach
  fun tearDown() {
    logger.detachAppender(appender)
  }

  private fun messages() = appender.list.map { it.formattedMessage }

  @Test
  fun `reports each bucket once when total divides evenly`() {
    val reporter = ProgressReporter(total = 100, reportAtPercent = 10)
    for (i in 0..100) reporter.report(i)

    val msgs = messages()
    assertThat(msgs).hasSize(11)
    assertThat(msgs.first()).contains("0%")
    assertThat(msgs.last()).contains("100%")
  }

  @Test
  fun `reports each bucket once when total does not divide evenly`() {
    val reporter = ProgressReporter(total = 333, reportAtPercent = 10)
    for (i in 0 until 333) reporter.report(i)

    val msgs = messages()
    // buckets 0..9 (max reached is 99%, since last index is 332/333)
    assertThat(msgs).hasSize(10)
    assertThat(msgs.first()).contains("0%")
    assertThat(msgs.last()).contains("90%")
  }

  @Test
  fun `does not repeat same bucket`() {
    val reporter = ProgressReporter(total = 1000, reportAtPercent = 25)
    for (i in 0..1000) reporter.report(i)

    val msgs = messages()
    // 0, 25, 50, 75, 100
    assertThat(msgs).hasSize(5)
  }

  @Test
  fun `zero total is a no-op`() {
    val reporter = ProgressReporter(total = 0, reportAtPercent = 10)
    reporter.report(0)
    assertThat(messages()).isEmpty()
  }
}
