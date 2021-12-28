package com.dibujaron.distanthorizon.orbiter.station.hold

import org.json.JSONArray

abstract class StationHold {

    open fun tick(){
        CommodityType.values().asSequence()
            .map{getCommodityStore(it)}
            .forEach{it.tick()}
    }

    open fun toJSON(): JSONArray {
        val commodities = JSONArray()
        CommodityType.values().asSequence()
            .map { getCommodityStore(it) }
            .map { it.createStoreJson() }
            .forEach { commodities.put(it) }
        return commodities
    }

    abstract fun getCommodityStore(type: CommodityType): CommodityStore
}