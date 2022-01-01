package com.dibujaron.distanthorizon.utils

import com.dibujaron.distanthorizon.DHServer

object TimeUtils {

    fun getCurrentTickAbsolute(): Int {
        return DHServer.getTickCount()
    }

    fun getCurrentTickInCycle(): Int {
        return getCurrentTickAbsolute() % DHServer.cycleLengthTicks
    }

    fun getCurrentCycleStartTick(): Int {
        return getCurrentTickAbsolute() / DHServer.cycleLengthTicks
    }

    private fun getNextCycleStartTick(): Int {
        return getCurrentCycleStartTick() + DHServer.cycleLengthTicks
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
        assert(cycleTick < DHServer.cycleLengthTicks)
    }

    fun ticksToSeconds(ticks: Double): Double {
        return ticks / DHServer.TICKS_PER_SECOND
    }

    fun secondsToTicks(seconds: Double): Double {
        return seconds * DHServer.TICKS_PER_SECOND
    }
}