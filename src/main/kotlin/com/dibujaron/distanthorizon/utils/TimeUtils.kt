package com.dibujaron.distanthorizon.utils

import com.dibujaron.distanthorizon.DHServer

object TimeUtils {

    fun getCurrentTickAbsolute(): Int {
        return DHServer.getTickCount()
    }

    fun getCurrentTickInCycle(): Int {
        return getCurrentTickAbsolute() % DHServer.CYCLE_LENGTH_TICKS
    }

    fun getCurrentCycleStartTick(): Int {
        return getCurrentTickAbsolute() / DHServer.CYCLE_LENGTH_TICKS
    }

    private fun getNextCycleStartTick(): Int {
        return getCurrentCycleStartTick() + DHServer.CYCLE_LENGTH_TICKS
    }

    fun getNextAbsoluteTimeOfCycleTick(cycleTick: Int): Int
    {
        checkCycleTick(cycleTick)
        val currentTick = getCurrentTickInCycle()
        return if(cycleTick >= currentTick){
            cycleTick //it's in the current cycle
        } else {
            val nextCycleStart = getNextCycleStartTick()
            nextCycleStart + cycleTick
        }
    }

    fun checkCycleTick(cycleTick: Int){
        assert(cycleTick >= 0)
        assert(cycleTick < DHServer.CYCLE_LENGTH_TICKS)
    }

    fun ticksToSeconds(ticks: Double): Double {
        return ticks / DHServer.TICKS_PER_SECOND
    }

    fun secondsToTicks(seconds: Double): Double {
        return seconds * DHServer.TICKS_PER_SECOND
    }
}