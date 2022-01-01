package com.dibujaron.distanthorizon.orbiter.station.passenger

import com.dibujaron.distanthorizon.DHModule
import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.utils.TimeUtils
import java.util.*

const val SYNC_TIME_TICKS = DHServer.TICKS_PER_SECOND * 3
const val PASSENGER_CLEAR_TICKS = DHServer.TICKS_PER_SECOND * 60 * 5
const val CLEAR_PASSENGERS_OLDER_THAN_MS = 1000 * 60 * 10

object WaitingRoomManager : DHModule {
    var lastSyncTick = 0
    var lastPassengerClearTick = 0
    private var passengerGenerateTimeTicks = 0
    private var passengerGroupMinSize = 8
    private var passengerGroupMaxSize = 16
    override fun moduleInit(serverProperties: Properties) {
        val passengerGenerateTimeSeconds = serverProperties.getProperty("passenger.generate.time.seconds", "120").toInt()
        passengerGenerateTimeTicks = passengerGenerateTimeSeconds * DHServer.TICKS_PER_SECOND
        passengerGroupMinSize = serverProperties.getProperty("passenger.group.size.min", "8").toInt()
        passengerGroupMaxSize = serverProperties.getProperty("passenger.group.size.max", "16").toInt()

    }

    fun getPassengerGenerateTimeTicks(): Int{
        return passengerGenerateTimeTicks
    }

    fun getPassengerGroupMinSize(): Int
    {
        return passengerGroupMinSize
    }

    fun getPassengerGroupMaxSize(): Int
    {
        return passengerGroupMaxSize
    }

    override fun tick() {
        syncTick()
        clearTick()
    }

    private fun syncTick(){
        val currentTick = TimeUtils.getCurrentTickAbsolute()
        if (currentTick - lastSyncTick > SYNC_TIME_TICKS) {
            BackgroundTaskManager.executeInBackground {
                val waitingGroups = DHServer.getDatabase().getPersistenceDatabase().selectWaitingPassengers()
                OrbiterManager.getStations()
                    .asSequence()
                    .map { it.waitingRoom }
                    .forEach { it.clearWaitingPassengers() }
                waitingGroups.forEach {
                    val sourceStationKey = it.station
                    val sourceStation = OrbiterManager.getStationByKey(sourceStationKey)!!
                    val waitingRoom = sourceStation.waitingRoom
                    waitingRoom.addWaitingPassengers(it.destinationStation, it.quantity)
                }
            }
            lastSyncTick = currentTick
        }
    }

    private fun clearTick() {
        if(DHServer.isMaster) {
            val currentTick = TimeUtils.getCurrentTickAbsolute()
            if (currentTick - lastPassengerClearTick > PASSENGER_CLEAR_TICKS) {
                BackgroundTaskManager.executeInBackground {
                    val priorTime = System.currentTimeMillis() - CLEAR_PASSENGERS_OLDER_THAN_MS
                    DHServer.getDatabase().getPersistenceDatabase().clearWaitingPassengersWaitingSinceBefore(priorTime)
                }
                lastPassengerClearTick = currentTick
            }
        }
    }
}