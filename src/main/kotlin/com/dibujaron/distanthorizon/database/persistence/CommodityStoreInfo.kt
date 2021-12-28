package com.dibujaron.distanthorizon.database.persistence

import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType

open class CommodityStoreInfo(
    val stationKey: StationKey,
    val commodityType: CommodityType,
    val price: Double,
    val quantity: Int
)