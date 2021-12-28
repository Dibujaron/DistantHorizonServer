package com.dibujaron.distanthorizon.utils

import org.junit.Test

class NoiseUtilsTest {

    @Test
    fun testNoise() {
        val seed = System.currentTimeMillis()
        for(i in 1..1000){
            val noise = NoiseUtils.getNoise(seed, i.toDouble())
            println(noise)
        }
    }
}