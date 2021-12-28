package com.dibujaron.distanthorizon.orbiter.station.hold.fixed

import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityStore
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.orbiter.station.hold.StationHold
import java.util.*

class FixedValueStationHold(stationProperties: Properties) : StationHold(){

    private val commodityStores: Map<CommodityType, CommodityStore> = CommodityType
        .values()
        .asSequence()
        .map { FixedValueCommodityStore(it, stationProperties) }
        .map { Pair(it.type, it) }
        .toMap()

    override fun getCommodityStore(type: CommodityType): CommodityStore {
        return commodityStores[type]!!
    }
}