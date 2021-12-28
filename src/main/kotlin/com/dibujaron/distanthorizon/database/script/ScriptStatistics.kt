package com.dibujaron.distanthorizon.database.script

import com.dibujaron.distanthorizon.orbiter.station.Station
import com.dibujaron.distanthorizon.utils.TimeUtils

interface ScriptStatistics {
    fun getSourceStation(): Station
    fun getDestinationStation(): Station
    fun getDepartureTick(): Int
    fun getNextPossibleDeparture(): Int {
        return TimeUtils.getNextAbsoluteTimeOfCycleTick(getDepartureTick())
    }

    //if we haven't departed yet.
    fun getEarliestPossibleArrivalUsingThisRoute(): Int {
        return getNextPossibleDeparture() + getDuration()
    }

    fun getDuration(): Int

    fun getArrivalTick(): Int {
        return getDepartureTick() + getDuration()
    }
}