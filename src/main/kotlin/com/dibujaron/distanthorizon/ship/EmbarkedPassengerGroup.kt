package com.dibujaron.distanthorizon.ship;

import com.dibujaron.distanthorizon.Vector2;
import com.dibujaron.distanthorizon.database.persistence.StationKey;
import com.dibujaron.distanthorizon.orbiter.station.Station
import com.dibujaron.distanthorizon.passenger.PassengerManager
import com.dibujaron.distanthorizon.utils.TimeUtils
import java.lang.IllegalStateException
import kotlin.math.roundToInt

class EmbarkedPassengerGroup(
    val originStation: StationKey,
    val originLocation: Vector2,
    val embarkTime: Long,
    val destinationStation: StationKey,
    val quantity: Int
) {
    fun calculateReward(ship: Ship, arrivedAtStation: Station): Int {
        if(arrivedAtStation.key != destinationStation){
            throw IllegalStateException("Arrived at station is not the same as the destination station!")
        }
        val arrivalLocation = arrivedAtStation.globalPosition()
        val shipAcceleration = ship.type.mainThrust
        val idealTravelTimeSeconds = TimeUtils.idealTravelTimeSeconds(arrivalLocation, originLocation, shipAcceleration)
        val trueTravelTimeSeconds = (System.currentTimeMillis() - embarkTime) / 1000.0

        val baseReward = PassengerManager.getBasePassengerRewardPerSecond() * idealTravelTimeSeconds
        val idealToTrueRatio = idealTravelTimeSeconds / trueTravelTimeSeconds //will always be less than one
        val delayScaledReward = baseReward * idealToTrueRatio
        val accelerationRatio = ship.getCurrentJourneyAccelerationRatio() //calculateReward should be called before dock is completed, wiping out this ratio.
        val accelerationScaledReward = delayScaledReward * accelerationRatio
        val quantityScaledReward = accelerationScaledReward * quantity
        return quantityScaledReward.roundToInt()
    }
}