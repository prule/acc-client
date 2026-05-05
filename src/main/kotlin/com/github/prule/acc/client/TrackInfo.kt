package com.github.prule.acc.client

/**
 * Stable, decoded snapshot of an [com.github.prule.acc.messages.AccBroadcastingInbound.TrackData]
 * message.
 *
 * Equality by [name] + [id] enables track-change detection without inspecting raw bytes.
 */
data class TrackInfo(
  val name: String,
  val id: Int,
  val meters: Int,
  val cameraSets: List<CameraSet>,
  val hudPages: List<String>,
) {
  data class CameraSet(val name: String, val cameras: List<String>)
}
