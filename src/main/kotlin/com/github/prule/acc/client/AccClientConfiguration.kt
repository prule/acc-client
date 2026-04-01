package com.github.prule.acc.client

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class AccClientConfiguration(
    val name: String,
    val port: Int = 9000,
    val updateMillis: Int = 1000,
    val connectionPassword: String = "asd",
    val serverIp: String = "127.0.0.1",
    val connectTimeout: Duration = 10.seconds,
    val retryPeriod: Duration = 10.seconds,
)
