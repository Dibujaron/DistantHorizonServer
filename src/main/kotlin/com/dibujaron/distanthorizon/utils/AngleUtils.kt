package com.dibujaron.distanthorizon.utils

import kotlin.math.floor

object AngleUtils {
    fun normaliseToRange(value: Double, start: Double, end: Double): Double
    {
        val width = end - start;
        val offsetValue = value - start;
        return (offsetValue - (floor(offsetValue / width) * width)) + start;
    }

    fun limitAngle(angle: Double): Double {
        return normaliseToRange(angle, 0.0, Math.PI * 2)
    }

    fun angularDiff(a: Double, b: Double): Double
    {
        return limitAngle(a) - limitAngle(b)
    }

}