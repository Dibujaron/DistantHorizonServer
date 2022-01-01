package com.dibujaron.distanthorizon.database.impl

import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.database.persistence.WaitingPassengerGroupInfo
import org.jetbrains.exposed.dao.id.EntityID

class WaitingPassengerGroupInfoInternal(
    val id: EntityID<Int>,
    station: StationKey,
    destinationStation: StationKey, quantity: Int, waitingSince: Long
) : WaitingPassengerGroupInfo(station, destinationStation, quantity, waitingSince)