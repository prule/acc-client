package io.github.prule.acc.client.simulator

import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class AccSimulatorConfiguration(
    val port: Int,
    val connectionPassword: String,
    val playbackEventsFile: Path,
)
