package com.dibujaron.distanthorizon.ship;

import com.dibujaron.distanthorizon.Vector2;
import com.dibujaron.distanthorizon.database.persistence.StationKey;

class EmbarkedPassengerGroup(
    val originStation:StationKey,
    val originLocation:Vector2,
    val embarkTime:Long,
    val destinationStation:StationKey,
    val quantity:Int
){}