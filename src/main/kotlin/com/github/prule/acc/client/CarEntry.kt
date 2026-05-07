package com.github.prule.acc.client

/**
 * Stable, decoded snapshot of an
 * [com.github.prule.acc.messages.AccBroadcastingInbound.EntryListCar] message.
 */
data class CarEntry(
  val carId: Int,
  val carModelType: Int,
  val teamName: String,
  val raceNumber: Int,
  val cupCategory: String,
  val currentDriverIndex: Int,
  val nationality: Int,
  val drivers: List<Driver>,
) {
  data class Driver(
    val firstName: String,
    val lastName: String,
    val shortName: String,
    val category: String,
    val nationality: Int,
  )
}
