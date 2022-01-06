package com.dibujaron.distanthorizon.passenger

import com.dibujaron.distanthorizon.DHModule
import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.utils.TimeUtils
import java.util.*

const val SYNC_TIME_TICKS = DHServer.TICKS_PER_SECOND * 3
const val PASSENGER_CLEAR_TICKS = DHServer.TICKS_PER_SECOND * 60 * 5

object PassengerManager : DHModule {
    var lastSyncTick = 0
    var lastPassengerClearTick = 0
    private var passengerGenerateTimeTicks = 0
    private var passengerGroupMinSize = 8
    private var passengerGroupMaxSize = 16
    private var basePassengerRewardPerSecond = 0.0
    private var clearPassengersOlderThanMs = 1000 * 60 * 10
    override fun moduleInit(serverProperties: Properties) {
        val passengerGenerateTimeSeconds = serverProperties.getProperty("passenger.generate.time.seconds", "120").toInt()
        passengerGenerateTimeTicks = passengerGenerateTimeSeconds * DHServer.TICKS_PER_SECOND
        passengerGroupMinSize = serverProperties.getProperty("passenger.group.size.min", "8").toInt()
        passengerGroupMaxSize = serverProperties.getProperty("passenger.group.size.max", "16").toInt()
        val clearPassengersOlderThanSeconds = serverProperties.getProperty("passenger.clear.time.seconds", "600").toInt()
        clearPassengersOlderThanMs = clearPassengersOlderThanSeconds * 1000
        //reward for carrying one passenger for one minute at ideal travel
        val basePassengerRewardPerMinute = serverProperties.getProperty("passenger.reward.per.minute", "100").toDouble()
        basePassengerRewardPerSecond = basePassengerRewardPerMinute / 60.0
        executeClear()
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

    fun getBasePassengerRewardPerSecond(): Double
    {
        return basePassengerRewardPerSecond
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
                executeClear()
                lastPassengerClearTick = currentTick
            }
        }
    }
    private fun executeClear(){
        if(DHServer.isMaster){
            BackgroundTaskManager.executeInBackground {
                val priorTime = System.currentTimeMillis() - clearPassengersOlderThanMs
                DHServer.getDatabase().getPersistenceDatabase().clearWaitingPassengersWaitingSinceBefore(priorTime)
            }
        }
    }
}