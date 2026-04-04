package com.github.prule.acc.client

class ClientState {
    var connectionId: Int = 0
    var focusedCarIndex: Int = 0
    var track: String? = null
    var car: CarModel? = null
    var carList: List<Int> = emptyList()

    override fun toString(): String {
        return "ClientState(connectionId=$connectionId, focusedCarIndex=$focusedCarIndex, track=$track, car=$car)"
    }

}