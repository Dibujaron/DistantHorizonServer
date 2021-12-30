package com.dibujaron.distanthorizon.docking

import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.orbiter.station.Station

class StationDockingPort(val station: Station, val relativePos: Vector2, val rotationDegrees: Double) : DockingPort {
    val relativeRotation = Math.toRadians(rotationDegrees)

    override fun globalPosition(): Vector2 {
        return station.globalPos() + relativePos.rotated(station.globalRotation())
    }

    override fun getVelocity(): Vector2 {
        return station.velocity()
    }

    override fun globalRotation(): Double {
        return station.globalRotation() + relativeRotation
    }

    override fun relativePosition(): Vector2 {
        return relativePos
    }

    override fun relativeRotation(): Double {
        return relativeRotation
    }
}