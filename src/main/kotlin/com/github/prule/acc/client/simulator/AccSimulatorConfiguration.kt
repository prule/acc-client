package com.github.prule.acc.client.simulator

import kotlinx.serialization.Serializable

@Serializable
data class AccSimulatorConfiguration(
    val port: Int,
    val connectionPassword: String,
    val playbackEventsFile: Source,
    /** milliseconds delay between each message sent */
    val delay: Long = 100,
)
