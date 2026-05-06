package com.github.prule.acc.client

import org.slf4j.LoggerFactory

class ProgressReporter(val total: Int, val reportAtPercent: Int = 10) {
  private val logger = LoggerFactory.getLogger(javaClass)

  fun report(progress: Int) {
    val percentage = progress * 100 / total
    if (percentage % reportAtPercent == 0) {
      logger.info("Progress: $percentage%")
    }
  }
}
