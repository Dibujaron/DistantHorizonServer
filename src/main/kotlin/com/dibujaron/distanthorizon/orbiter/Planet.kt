package com.dibujaron.distanthorizon.orbiter

import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.ship.ShipState
import org.json.JSONObject
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

class Planet(parentName: String?, planetName: String, properties: Properties): Orbiter(parentName, planetName, properties){
    val type = properties.getProperty("type").toString()
    val mass = loadMass(properties)
    val tidalLock = if(properties.containsKey("tidalLock")) properties.getProperty("tidalLock")!!.toBoolean() else false
    val rotationSpeed: Double = if(properties.containsKey("rotationSpeed")) properties.getProperty("rotationSpeed").toDouble() else 0.0
    val minOrbitalRadius = properties.getProperty("minOrbitalRadius").toInt()
    val minRadiusSquared = minOrbitalRadius * minOrbitalRadius

    override fun scale(): Double{
        return if (properties.containsKey("scale")) {
            properties.getProperty("scale").toDouble()
        } else {
            1.0
        }
    }

    override fun createOrbiterJson(): JSONObject {
        val retval = super.createOrbiterJson()
        retval.put("tidal_lock", tidalLock)
        retval.put("rotation_speed", rotationSpeed)
        retval.put("scale", scale())
        retval.put("type", type)
        retval.put("mass", mass)
        retval.put("min_orbital_altitude", minOrbitalRadius)
        retval.put("display_name", displayName)
        return retval
    }

    fun getStableOrbit(radius: Double): ShipState
    {
        val planetPos = this.globalPos()
        val offset = Vector2(radius, 0)
        val startingPos = planetPos + offset
        val g = OrbiterManager.GRAVITY_CONSTANT
        val speed = sqrt((g * mass) / radius)
        println("starting speed is $speed")
        val angle = offset.angle
        val startingVelocity = Vector2(speed, 0).rotated(angle)
        val rotation = angle - Math.PI / 2
        return ShipState(startingPos, rotation, startingVelocity)
    }
}

fun loadMass(properties: Properties): Double{
    if (properties.containsKey("massBase") && properties.containsKey("massExp")) {
        val massBase = properties.getProperty("massBase").toDouble()
        val massExp = properties.getProperty("massExp").toDouble()
        return massBase * 10.0.pow(massExp);
    } else {
        throw IllegalArgumentException("Planet properties must contain massBase and massExp")
    }
}