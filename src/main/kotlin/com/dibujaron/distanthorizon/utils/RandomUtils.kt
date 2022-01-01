package com.dibujaron.distanthorizon.utils

import kotlin.math.floor

object RandomUtils {

    //lower inclusive, upper exclusive
    fun randIntBetween(lower: Int, upper: Int): Int
    {
        val rand = Math.random()
        val diff = upper - lower
        val scaled = rand * diff
        val shifted = scaled + lower
        val floored = floor(shifted).toInt()
        return floored
    }
}