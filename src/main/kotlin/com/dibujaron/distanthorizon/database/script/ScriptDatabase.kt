package com.dibujaron.distanthorizon.database.script

import com.dibujaron.distanthorizon.database.persistence.ActorInfo
import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.orbiter.Station
import com.dibujaron.distanthorizon.ship.ShipClass
import com.dibujaron.distanthorizon.ship.ShipState

interface ScriptDatabase {
    fun selectStationsWithScripts(): List<StationKey>
    fun selectScriptsForStation(sourceStation: StationKey): List<ScriptReader>
    fun selectAvailableScript(sourceStation: StationKey, targetStation: StationKey, earliestDepartureTick: Int, latestDepartureTick: Int): ScriptReader?
    fun selectAvailableScriptToAnywhere(sourceStation: StationKey, earliestDepartureTick: Int, latestDepartureTick: Int): ScriptReader?
    fun beginLoggingScript(actor: ActorInfo?, sourceStation: StationKey, startState: ShipState, shipClass: ShipClass): ScriptWriter
    fun shutdown()
}