package com.dibujaron.distanthorizon.database.script

import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.orbiter.Station
import com.dibujaron.distanthorizon.ship.ShipInputs

interface ScriptWriter {
    fun writeAction(action: ShipInputs)
    fun completeScript(dockedStation: StationKey)
}