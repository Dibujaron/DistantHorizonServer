package com.dibujaron.distanthorizon.orbiter.station.passenger

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.database.persistence.StationKey
import java.util.concurrent.ConcurrentHashMap

class StationPassengerWaitingRoom(val stationKey: StationKey) {
    val waitingPassengers: MutableMap<StationKey, Int> = ConcurrentHashMap()

    init {
        DHServer.getDatabase().getPersistenceDatabase().selectWaitingPassengersAtStation(stationKey)
            .forEach { updateWaitingPassengersToStation(it.destinationStation, it.quantity) }
    }

    fun updateWaitingPassengersToStation(destStation: StationKey, newQuantity: Int) {
        waitingPassengers[destStation] = newQuantity
    }

    fun tick() {
        if (DHServer.isMaster) {

        }
    }
}