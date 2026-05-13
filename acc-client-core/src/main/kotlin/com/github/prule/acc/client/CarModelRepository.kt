package com.github.prule.acc.client

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import io.blackmo18.kotlin.grass.dsl.grass

class CarModelRepository {
  @OptIn(ExperimentalStdlibApi::class)
  private val carModels: List<CarModel> by lazy {
    val resourceStream =
      javaClass.getResourceAsStream("/com/github/prule/acc/client/car_model_type.csv")
        ?: throw IllegalStateException(
          "Resource not found: /com/github/prule/acc/client/car_model_type.csv"
        )
    val csvContents = csvReader().readAllWithHeader(resourceStream)
    grass<CarModel>().harvest(csvContents)
  }

  fun findById(id: Int): CarModel? = carModels.find { it.id == id }
}

data class CarModel(val id: Int, val name: String)
