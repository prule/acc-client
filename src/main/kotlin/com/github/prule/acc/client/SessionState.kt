package com.github.prule.acc.client

import com.github.prule.acc.messages.AccBroadcastingInbound

class SessionState {
  var track: String? = null
  var carList: List<Int> = emptyList()
  val carMap = mutableMapOf<Int, AccBroadcastingInbound.EntryListCar>()
  val laps = mutableMapOf<Int, Map<Int, AccBroadcastingInbound.RealtimeCarUpdate>>()
  val lapsCompleted = mutableMapOf<Int, Map<Int, AccBroadcastingInbound.BroadcastingEvent>>()

  override fun toString(): String {
    return "SessionState(track=$track, list=$carList)"
  }
}
