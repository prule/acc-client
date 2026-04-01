package com.github.prule.acc.client

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import io.blackmo18.kotlin.grass.dsl.grass

class CarModelRepository {
    @OptIn(ExperimentalStdlibApi::class)
    private val carModels: List<CarModel> by lazy {
        val csvContents = csvReader().readAllWithHeader(javaClass.getResourceAsStream("/io/github/prule/acc/client/car_model_type.csv")!!)
        grass<CarModel>().harvest(csvContents)
    }

    fun findById(id: Int): CarModel? =
        carModels.find {
            it.id == id
        }
}

data class CarModel(
    val id: Int,
    val name: String,
)
