package com.dibujaron.distanthorizon.database.persistence

open class WaitingPassengerGroupInfo(
    val station: StationKey,
    val destinationStation: StationKey,
    val quantity: Int,
    val waitingSince: Long
)