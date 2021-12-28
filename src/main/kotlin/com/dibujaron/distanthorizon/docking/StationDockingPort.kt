package com.dibujaron.distanthorizon.docking

import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.orbiter.station.Station

class StationDockingPort(val station: Station, val relativePos: Vector2, val rotationDegrees: Double) : DockingPort {
    val relativeRotation = Math.toRadians(rotationDegrees)

    override fun globalPosition(): Vector2 {
        return globalPosAtTick(0.0)
    }

    fun globalPosAtTick(tick: Double): Vector2 {
        return station.globalPosAtTick(tick) + relativePos.rotated(station.globalRotationAtTick(tick))

    }

    override fun getVelocity(): Vector2 {
        return velocityAtTick(0.0)
    }

    fun velocityAtTick(tickOffset: Double): Vector2 {
        return station.velocityAtTick(tickOffset)
    }

    override fun globalRotation(): Double {
        return globalRotationAtTick(0.0)
    }

    fun globalRotationAtTick(tickOffset: Double): Double {
        return station.globalRotationAtTick(tickOffset) + relativeRotation
    }

    override fun relativePosition(): Vector2 {
        return relativePos
    }

    override fun relativeRotation(): Double {
        return relativeRotation
    }
}