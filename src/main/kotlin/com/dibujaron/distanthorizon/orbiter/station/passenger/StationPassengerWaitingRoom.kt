package com.dibujaron.distanthorizon.orbiter.station.passenger

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.utils.RandomUtils
import com.dibujaron.distanthorizon.utils.TimeUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class StationPassengerWaitingRoom(val stationKey: StationKey, stationProperties: Properties) {
    private val groupsToGenerate = stationProperties.getProperty("passengers.generation.rate")!!.toDouble()
    private val waitingPassengers: MutableMap<StationKey, Int> = ConcurrentHashMap()

    init {
        DHServer.getDatabase().getPersistenceDatabase().selectWaitingPassengersAtStation(stationKey)
            .forEach { addWaitingPassengers(it.destinationStation, it.quantity) }
    }

    fun clearWaitingPassengers(){
        waitingPassengers.clear()
    }

    fun addWaitingPassengers(destinationStation: StationKey, quantity: Int)
    {
        val existingQuantity = waitingPassengers.getOrDefault(destinationStation, 0)
        val newQuantity = existingQuantity + quantity
        waitingPassengers[destinationStation] = newQuantity
    }

    var lastGenerateTick = 0
    fun tick() {
        if (DHServer.isMaster) {
            val currentTick = TimeUtils.getCurrentTickAbsolute()
            if (currentTick - lastGenerateTick > WaitingRoomManager.getPassengerGenerateTimeTicks()) {
                val groupsToGenerate = getGroupsToGenerate()
                val minSize = WaitingRoomManager.getPassengerGroupMinSize()
                val maxSize = WaitingRoomManager.getPassengerGroupMaxSize()
                for(i in (0..groupsToGenerate)){
                    val size = RandomUtils.randIntBetween(minSize, maxSize)
                }
                lastGenerateTick = currentTick
            }
        }
    }

    private fun getGroupsToGenerate(): Int{
        val wholeNumberGroups = floor(groupsToGenerate).toInt()
        val partialGroups = groupsToGenerate - wholeNumberGroups
        val rand = Math.random()
        return if(rand <= partialGroups){
            wholeNumberGroups + 1
        } else {
            wholeNumberGroups
        }
    }
}