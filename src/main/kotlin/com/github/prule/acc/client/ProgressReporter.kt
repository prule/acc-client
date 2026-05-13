package com.github.prule.acc.client

import org.slf4j.LoggerFactory

class ProgressReporter(val total: Int, val reportAtPercent: Int = 10) {
  private val logger = LoggerFactory.getLogger(javaClass)

  fun report(progress: Int) {
    val percentage = progress.toDouble() * 100.0 / total.toDouble()
    if (percentage % reportAtPercent.toDouble() == 0.0) {
      logger.info("\r Progress: $percentage%")
    }
  }
}
