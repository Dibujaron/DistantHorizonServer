package com.dibujaron.distanthorizon.database.persistence

import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.orbiter.Station
import org.json.JSONObject

open class ActorInfo(
    val uniqueID: Int,
    val displayName: String,
    val balance: Int,
    val lastDockedStation: StationKey?,
    val ship: ShipInfo
){
    open fun toJSON(): JSONObject {
        val station: Station? = if(lastDockedStation == null) null else OrbiterManager.getStationByKey(lastDockedStation);
        val r = JSONObject()
        r.put("unique_id", uniqueID)
        r.put("display_name", displayName)
        r.put("balance", balance)
        r.put("station_name", station?.name)
        r.put("station_display_name", station?.displayName)
        r.put("ship", ship.toJSON())
        return r
    }
}