package com.dibujaron.distanthorizon

import com.dibujaron.distanthorizon.utils.AngleUtils
import org.json.JSONObject
import kotlin.math.*


class Vector2(val x: Double, val y: Double) {

    val angle: Double by lazy { atan2(y, x) }
    val lengthSquared: Double by lazy { x * x + y * y }
    val length: Double by lazy { sqrt(lengthSquared) }
    val angleLimited: Double by lazy { AngleUtils.limitAngle(angle)}
    constructor(x: Int, y: Int) : this(x.toDouble(), y.toDouble())
    constructor(x: Int, y: Double) : this(x.toDouble(), y)
    constructor(x: Double, y: Int) : this(x, y.toDouble())

    operator fun plus(other: Vector2): Vector2 {
        return Vector2(x + other.x, y + other.y)
    }

    operator fun minus(other: Vector2): Vector2 {
        return Vector2(x - other.x, y - other.y)
    }

    operator fun times(other: Double): Vector2 {
        return Vector2(x * other, y * other)
    }

    operator fun times(other: Int): Vector2 {
        return Vector2(x * other, y * other)
    }

    operator fun div(other: Double): Vector2 {
        return Vector2(x / other, y / other)
    }

    operator fun div(other: Int): Vector2 {
        return Vector2(x / other, y / other)
    }

    //credit to Johan Larsson/StackOverflow
    fun rotated(radians: Double): Vector2 {
        val ca: Double = cos(radians);
        val sa: Double = sin(radians);
        return Vector2(ca * x - sa * y, sa * x + ca * y);
    }

    fun normalized(): Vector2 {
        return if (length != 0.0) {
            Vector2(x / length, y / length)
        } else {
            Vector2(0, 0)
        }
    }

    fun toJSON(): JSONObject
    {
        val retval = JSONObject()
        retval.put("x", x)
        retval.put("y", y)
        return retval
    }

    override fun toString(): String
    {
        return "($x,$y)"
    }

    companion object{
        val ZERO = Vector2(0,0)
    }
}