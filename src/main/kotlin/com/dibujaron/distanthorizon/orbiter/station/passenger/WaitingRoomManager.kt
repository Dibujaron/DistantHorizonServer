package com.dibujaron.distanthorizon.orbiter.station.passenger

import com.dibujaron.distanthorizon.DHModule
import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.orbiter.Orbiter
import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.utils.TimeUtils

const val SYNC_TIME_TICKS = DHServer.TICKS_PER_SECOND * 3
const val PASSENGER_CLEAR_TICKS = DHServer.TICKS_PER_SECOND * 60 * 5
const val CLEAR_PASSENGERS_OLDER_THAN_MS = 1000 * 60 * 10

object WaitingRoomManager : DHModule {
    var lastSyncTick = 0
    var lastPassengerClearTick = 0
    override fun tick() {
        val currentTick = TimeUtils.getCurrentTickAbsolute()
        if (currentTick - lastSyncTick > SYNC_TIME_TICKS) {
            BackgroundTaskManager.executeInBackground {
                val waitingGroups = DHServer.getDatabase().getPersistenceDatabase().selectWaitingPassengers()
                OrbiterManager.getStations()
                    .asSequence()
                    .map { it.waitingRoom }
                    .map { it.waitingPassengers }
                    .forEach { it.clear() }
                waitingGroups.forEach {
                    val sourceStationKey = it.station
                    val sourceStation = OrbiterManager.getStationByKey(sourceStationKey)!!
                    val waitingRoom = sourceStation.waitingRoom
                    val waitingPassengers = waitingRoom.waitingPassengers
                    val existingQuantity = waitingPassengers.getOrDefault(it.destinationStation, 0)
                    val newQuantity = existingQuantity + it.quantity
                    waitingPassengers[it.destinationStation] = newQuantity
                }
            }
            lastSyncTick = currentTick
        }
        if (currentTick - lastPassengerClearTick > PASSENGER_CLEAR_TICKS) {
            BackgroundTaskManager.executeInBackground {
                val priorTime = System.currentTimeMillis() - CLEAR_PASSENGERS_OLDER_THAN_MS
                DHServer.getDatabase().getPersistenceDatabase().clearWaitingPassengersWaitingSinceBefore(priorTime)
            }
            lastPassengerClearTick = currentTick
        }
    }
}