package com.dibujaron.distanthorizon.utils

import com.raylabz.opensimplex.OpenSimplexNoise

object NoiseUtils {
    private val generatorMap = HashMap<Long, OpenSimplexNoise>()

    fun getNoise(seed: Long, timeOffset: Double): Double {
        val generator = generatorMap.getOrDefault(seed, OpenSimplexNoise(seed))

        return generator.getNoise2D(timeOffset, 0.0).value
    }
}