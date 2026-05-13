package com.github.prule.acc.client.simulator

import kotlinx.serialization.Serializable

@Serializable
data class AccSimulatorConfiguration(
  val port: Int,
  val connectionPassword: String,
  val playbackEventsFile: Source,
  /** milliseconds delay between each message sent */
  val delay: Long = 10,
  /** maximum number of events to emit per session, defaults to max integer value */
  val maxEvents: Int = Int.MAX_VALUE,
  /** only emit player events, and skip other cars if true */
  val onlyPlayerEvents: Boolean = false,
)
