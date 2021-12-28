package com.dibujaron.distanthorizon.database.persistence

import com.dibujaron.distanthorizon.Vector2

open class EmbarkedPassengerGroupInfo (
    val originStation: StationKey,
    val originLocation: Vector2,
    val embarkTime: Long,
    val destinationStation: StationKey,
    val quantity: Int
        )