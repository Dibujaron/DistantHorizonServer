package com.dibujaron.distanthorizon.orbiter.station.passenger

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.passenger.PassengerManager
import com.dibujaron.distanthorizon.utils.RandomUtils
import com.dibujaron.distanthorizon.utils.TimeUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class StationPassengerWaitingRoom(private val stationKey: StationKey, stationProperties: Properties) {
    //this is the number of groups that generate every 2 minutes.
    //this number times 5 will be available at all times.
    private val passengerFactor = stationProperties.getProperty("passenger.factor")!!.toDouble()
    val waitingPassengers: MutableMap<StationKey, Int> = ConcurrentHashMap()

    init {
        DHServer.getDatabase().getPersistenceDatabase().selectWaitingPassengersAtStation(stationKey)
            .forEach { addWaitingPassengers(it.destinationStation, it.quantity) }
    }

    fun clearWaitingPassengers() {
        waitingPassengers.clear()
    }

    fun addWaitingPassengers(destinationStation: StationKey, quantity: Int) {
        val existingQuantity = waitingPassengers.getOrDefault(destinationStation, 0)
        val newQuantity = existingQuantity + quantity
        waitingPassengers[destinationStation] = newQuantity
    }

    var lastGenerateTick = 0
    fun tick() {
        if (DHServer.isMaster) {
            val currentTick = TimeUtils.getCurrentTickAbsolute()
            if (currentTick - lastGenerateTick > PassengerManager.getPassengerGenerateTimeTicks()) {
                val groupCountToGenerate = getGroupCountToGenerate()
                val minSize = PassengerManager.getPassengerGroupMinSize()
                val maxSize = PassengerManager.getPassengerGroupMaxSize()
                val weightedStations = OrbiterManager.getStations()
                    .filter { it.navigable }
                    .filter{it.key != this.stationKey}
                    .map { Pair(it.key, it.waitingRoom.passengerFactor) }
                val groupsToGenerate = (0..groupCountToGenerate).map {
                    val destinationStation = RandomUtils.weightedRandom(weightedStations)
                    val quantity = RandomUtils.randIntBetween(minSize, maxSize)
                    Pair(destinationStation, quantity)
                }
                groupsToGenerate.forEach { addWaitingPassengers(it.first, it.second) }
                BackgroundTaskManager.executeInBackground {
                    val persistDB = DHServer.getDatabase().getPersistenceDatabase()
                    groupsToGenerate.forEach {
                        persistDB.addWaitingPassengers(
                            stationKey,
                            it.first,
                            it.second,
                            System.currentTimeMillis()
                        )
                    }
                }
                lastGenerateTick = currentTick
            }
        }
    }

    fun toJSON(): JSONArray {
        val result = JSONArray()
        waitingPassengers.forEach {
            val waitingPassengersObject = JSONObject()
            val destinationStation = OrbiterManager.getStationByKey(it.key)!!
            waitingPassengersObject.put("destination_station", destinationStation.name)
            waitingPassengersObject.put("destination_station_display_name", destinationStation.displayName)
            waitingPassengersObject.put("quantity", it.value)
            result.put(waitingPassengersObject)
        }
        return result
    }


    private fun getGroupCountToGenerate(): Int {
        val wholeNumberGroups = floor(passengerFactor).toInt()
        val partialGroups = passengerFactor - wholeNumberGroups
        val rand = Math.random()
        return if (rand <= partialGroups) {
            wholeNumberGroups + 1
        } else {
            wholeNumberGroups
        }
    }
}