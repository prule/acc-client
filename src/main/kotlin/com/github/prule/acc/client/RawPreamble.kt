package com.github.prule.acc.client

/**
 * Last-seen raw bytes for each preamble message type. Preserves byte-perfect content for replay /
 * recording listeners that need the original UDP payload.
 */
data class RawPreamble(
  val trackData: ByteArray?,
  val entryList: ByteArray?,
  val carEntries: Map<Int, ByteArray>,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is RawPreamble) return false
    if (trackData != null) {
      if (other.trackData == null) return false
      if (!trackData.contentEquals(other.trackData)) return false
    } else if (other.trackData != null) return false
    if (entryList != null) {
      if (other.entryList == null) return false
      if (!entryList.contentEquals(other.entryList)) return false
    } else if (other.entryList != null) return false
    if (carEntries.keys != other.carEntries.keys) return false
    return carEntries.all { (k, v) -> other.carEntries[k]?.contentEquals(v) == true }
  }

  override fun hashCode(): Int {
    var result = trackData?.contentHashCode() ?: 0
    result = 31 * result + (entryList?.contentHashCode() ?: 0)
    result = 31 * result + carEntries.entries.sumOf { (k, v) -> 31 * k + v.contentHashCode() }
    return result
  }
}
