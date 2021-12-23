package com.dibujaron.distanthorizon.orbiter

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.utils.TimeUtils
import org.json.JSONObject
import java.util.*
import kotlin.math.*

abstract class Orbiter(private val parentName: String?, val name: String, val properties: Properties) {
    val displayName = properties.getProperty("displayName").toString()
    var startingPos: Vector2 = Vector2(0, 0)
    var initialized = false;
    var parent: Planet? = null;

    var orbitalSpeed: Double = 0.0
    var relativePos: Vector2 = Vector2(0, 0)
    var orbitalRadius: Double = 0.0
    var angularVelocityPerSecond: Double = 0.0
    var angularVelocityPerTick: Double = 0.0
    open fun scale(): Double {
        return 1.0
    }

    //called after all of the orbiters have loaded from file.
    fun initialize() {
        if (!initialized) {
            if (parentName == null) {
                startingPos = loadStartingPosition(properties)
                orbitalSpeed = 0.0
                orbitalRadius = startingPos.length
            } else {
                val foundParent: Planet? = OrbiterManager.getPlanet(parentName)
                if (foundParent == null) {
                    throw IllegalArgumentException("parent planet $parentName not found.")
                } else {
                    foundParent.initialize()
                    val unadjusted = loadStartingPosition(properties)
                    startingPos = adjustOrbitalRadiusToMatchCycleLength(unadjusted, foundParent.mass)
                    orbitalRadius = startingPos.length
                    orbitalSpeed = sqrt((OrbiterManager.GRAVITY_CONSTANT * foundParent.mass) / orbitalRadius)
                    parent = foundParent;

                }
            }
            relativePos = startingPos
            angularVelocityPerSecond = if (orbitalRadius > 0) {
                orbitalSpeed / orbitalRadius
            } else {
                0.0
            }
            angularVelocityPerTick = angularVelocityPerSecond / DHServer.TICKS_PER_SECOND
            initialized = true;
        }
    }

    open fun createOrbiterJson(): JSONObject {
        val retval = JSONObject()
        retval.put("name", name)
        retval.put("relative_pos", relativePos.toJSON())
        retval.put("orbital_radius", orbitalRadius)
        retval.put("angular_velocity", angularVelocityPerSecond)
        retval.put("angular_pos", relativePos.angle)
        retval.put("parent", parentName?:"")
        return retval
    }

    open fun tick() {
        if(TimeUtils.getCurrentTickInCycle() == 0){
            val diff = (startingPos - relativePos).lengthSquared
            if(diff > 1.0){
                throw IllegalStateException("Orbiter drift is too large!")
            }
            relativePos = startingPos //just to eliminate any possible wobble, at the end of a cycle we reset to exactly the start.
        }
        relativePos = relativePosAtTick(1.0) //this is tricky but correct.
    }


    fun globalPos(): Vector2 {
        return globalPosAtTick(0.0)
    }

    fun velocity(): Vector2 {
        return velocityAtTick(0.0)
    }

    fun velocityAtTick(tickOffset: Double): Vector2 {
        return (globalPosAtTick(tickOffset + 1) - globalPosAtTick(tickOffset)) * DHServer.TICKS_PER_SECOND
    }

    fun globalPosAtTick(tickOffset: Double): Vector2 {
        val parent = this.parent
        return if (parent == null) {
            relativePos
        } else {
            val parentPos = parent.globalPosAtTick(tickOffset)
            parentPos + relativePosAtTick(tickOffset)
        }
    }

    fun getStar(): Orbiter {
        val p = parent
        return p?.getStar() ?: this
    }

    override fun toString(): String {
        return name
    }

    fun relativePosAtTime(timeOffset: Double): Vector2 {
        return if (relativePos.lengthSquared == 0.0) {
            relativePos
        } else {
            val angleFromParent: Double = relativePos.angle
            val angleOffset: Double = angularVelocityPerSecond * timeOffset
            val newAngle = angleFromParent + angleOffset
            val newAngleVector = Vector2(cos(newAngle), sin(newAngle))
            newAngleVector * orbitalRadius
        }
    }

    fun relativePosAtTick(tickOffset: Double): Vector2 {
        return if (relativePos.lengthSquared == 0.0) {
            relativePos
        } else {
            val angleOffset: Double = angularVelocityPerTick * tickOffset
            relativePos.rotated(angleOffset)
        }
    }

    private fun loadStartingPosition(properties: Properties): Vector2 {
        return if (properties.containsKey("posX") && properties.containsKey("posY")) {
            val posX = properties.getProperty("posX").toDouble()
            val posY = properties.getProperty("posY").toDouble()
            Vector2(posX, posY)
        } else if (properties.containsKey("orbitalRadius")) {
            val orbitalRadius = properties.getProperty("orbitalRadius").toInt()
            //get a random, but consistent, number to be the starting angle.
            var random = Objects.hash(name, parentName).toDouble()
            Vector2(orbitalRadius, 0).rotated(random)
        } else {
            throw IllegalArgumentException("Properties file must contain posX,posY or orbitalRadius!")
        }
    }
}

fun adjustOrbitalRadiusToMatchCycleLength(originalPos: Vector2, parentMass: Double): Vector2 {
    val originalRadius = originalPos.length
    val originalPeriodSeconds = periodFromRadius(originalRadius, OrbiterManager.GRAVITY_CONSTANT, parentMass)
    val originalPeriod = TimeUtils.secondsToTicks(originalPeriodSeconds)
    val possiblePeriods = DHServer.FACTORS_OF_CYCLE_LENGTH
    var lowerPossibility = possiblePeriods.floor(floor(originalPeriod).toInt())
    if(lowerPossibility == null) lowerPossibility = 0
    var higherPossibility = possiblePeriods.ceiling(ceil(originalPeriod).toInt())
    if(higherPossibility == null) higherPossibility = DHServer.CYCLE_LENGTH_TICKS
    val lowerDiff = abs(originalPeriod - lowerPossibility)
    val higherDiff = abs(higherPossibility - originalPeriod)
    val result = if(lowerDiff < higherDiff) lowerPossibility else higherPossibility
    val resultSeconds = TimeUtils.ticksToSeconds(result.toDouble())
    val newRadius = radiusFromPeriod(resultSeconds, OrbiterManager.GRAVITY_CONSTANT, parentMass)
    return originalPos.normalized() * newRadius
}

fun periodFromRadius(r: Double, g: Double, m: Double): Double {
    return sqrt((r * r * r * 4 * PI * PI) / (g * m))
}

fun radiusFromPeriod(p: Double, g: Double, m: Double): Double {
    return ((g * m * p * p) / (4 * PI * PI)).pow(1.0 / 3.0)
}