package com.dibujaron.distanthorizon.orbiter.station.passenger

import com.dibujaron.distanthorizon.DHModule
import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.utils.TimeUtils

const val UPDATE_TIME_TICKS = DHServer.TICKS_PER_SECOND * 3

object WaitingRoomManager: DHModule {

    var lastUpdateTick = 0
    override fun tick() {
        val currentTick = TimeUtils.getCurrentTickAbsolute()
        if (currentTick - lastUpdateTick > UPDATE_TIME_TICKS) {
            BackgroundTaskManager.executeInBackground {
                DHServer.getDatabase().getPersistenceDatabase().selectWaitingPassengers()
                    .forEach {
                        val sourceStationKey = it.station
                        val sourceStation = OrbiterManager.getStationByKey(sourceStationKey)!!
                        sourceStation.waitingRoom.updateWaitingPassengersToStation(it.destinationStation, it.quantity)
                    }
            }
            lastUpdateTick = currentTick
        }

    }
}