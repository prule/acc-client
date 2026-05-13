package com.github.prule.acc.client

import org.slf4j.LoggerFactory

class ProgressReporter(val total: Int, val reportAtPercent: Int = 10) {
  private val logger = LoggerFactory.getLogger(javaClass)
  private var lastReportedBucket = -1

  fun report(progress: Int) {
    if (total <= 0) return
    val percentage = progress.toDouble() * 100.0 / total.toDouble()
    val bucket = (percentage / reportAtPercent).toInt()
    if (bucket > lastReportedBucket) {
      lastReportedBucket = bucket
      logger.info("\r Progress: ${bucket * reportAtPercent}%")
    }
  }
}
