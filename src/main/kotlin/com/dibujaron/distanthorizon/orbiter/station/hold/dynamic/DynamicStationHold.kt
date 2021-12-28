package com.dibujaron.distanthorizon.orbiter.station.hold.dynamic

import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityStore
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.orbiter.station.hold.StationHold
import java.util.*

class DynamicStationHold(val stationKey: StationKey, stationProperties: Properties) : StationHold(){

    private val commodityStores: Map<CommodityType, CommodityStore> = CommodityType
        .values()
        .asSequence()
        .map { DynamicCommodityStore(it, stationKey, stationProperties) }
        .map { Pair(it.type, it) }
        .toMap()

    override fun getCommodityStore(type: CommodityType): CommodityStore {
        return commodityStores[type]!!
    }

}