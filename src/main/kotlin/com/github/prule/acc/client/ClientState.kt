package com.github.prule.acc.client

class ClientState {
    var connectionId: Int = 0
    var focusedCarIndex: Int = 0

    override fun toString(): String {
        return "ClientState(connectionId=$connectionId, focusedCarIndex=$focusedCarIndex)"
    }

}