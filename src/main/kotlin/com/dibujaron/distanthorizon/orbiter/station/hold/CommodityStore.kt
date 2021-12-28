package com.dibujaron.distanthorizon.orbiter.station.hold

import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import org.json.JSONObject

abstract class CommodityStore(val type: CommodityType) {
    val displayName = type.displayName

    abstract fun getQuantityAvailable(): Int
    abstract fun updateQuantityAvailable(delta: Int)

    abstract fun getPrice(): Int
    abstract fun tick()

    fun createStoreJson(): JSONObject {
        val retval = JSONObject()
        retval.put("identifying_name", type.identifyingName)
        retval.put("display_name", displayName)
        retval.put("price", getPrice())
        retval.put("quantity_available", getQuantityAvailable())
        return retval
    }
}