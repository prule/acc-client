package com.github.prule.acc.client

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CarModelRepositoryTest {

  private val repository = CarModelRepository()

  @Test
  fun `should find car model by id`() {
    val carModel = repository.findById(0)

    assertThat(carModel).isNotNull
    assertThat(carModel?.id).isEqualTo(0)
    assertThat(carModel?.name).isEqualTo("Porsche 991 GT3 R")
  }

  @Test
  fun `should return null for non-existent id`() {
    val carModel = repository.findById(-1)

    assertThat(carModel).isNull()
  }

  @Test
  fun `should find all known car models from csv`() {
    // Just a sanity check for a few known ones
    assertThat(repository.findById(30)?.name).isEqualTo("BMW M4 GT3")
    assertThat(repository.findById(32)?.name).isEqualTo("Ferrari 296 GT3")
    assertThat(repository.findById(86)?.name).isEqualTo("Porsche 935")
  }
}
