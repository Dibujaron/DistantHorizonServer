package com.dibujaron.distanthorizon.utils

import java.lang.IllegalStateException
import kotlin.math.floor

object RandomUtils {

    //lower inclusive, upper exclusive
    fun randIntBetween(lower: Int, upper: Int): Int
    {
        val rand = Math.random()
        val diff = upper - lower
        val scaled = rand * diff
        val shifted = scaled + lower
        return floor(shifted).toInt()
    }

    fun <T> weightedRandom(weightedInputs: List<Pair<T, Double>>): T
    {
        val sumWeights = weightedInputs.asSequence().map{it.second}.sum()
        val random = Math.random() * sumWeights
        var remaining = random
        for(weightedInput in weightedInputs){
            val weight = weightedInput.second
            if(weight >= remaining){
                return weightedInput.first
            } else {
                remaining -= weight
            }
        }
        throw IllegalStateException("Should never get here, weightedRandom failed to resolve")
    }
}