package com.dibujaron.distanthorizon.database.script

import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.orbiter.Station
import com.dibujaron.distanthorizon.ship.ShipClass
import com.dibujaron.distanthorizon.ship.ShipInputs
import com.dibujaron.distanthorizon.ship.ShipState
import com.dibujaron.distanthorizon.utils.TimeUtils

interface ScriptReader {

    fun copy(): ScriptReader

    fun getDepartureTick(): Int

    fun getSourceStation(): StationKey

    fun getDestinationStation(): StationKey

    fun getStartingState(): ShipState

    fun getShipClass(): ShipClass

    fun hasNextAction(): Boolean

    fun nextActionShouldFire(): Boolean

    fun getNextAction(): ShipInputs

    fun getDuration(): Int

    fun getNextPossibleDeparture(): Int {
        return TimeUtils.getNextAbsoluteTimeOfCycleTick(getDepartureTick())
    }

    //if we haven't departed yet.
    fun getEarliestPossibleArrivalUsingThisRoute(): Int {
        return getNextPossibleDeparture() + getDuration()
    }
}