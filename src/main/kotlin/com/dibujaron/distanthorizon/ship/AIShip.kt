package com.dibujaron.distanthorizon.ship

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.database.script.ScriptReader

class AIShip(private val scriptReader: ScriptReader) : Ship(
    null,
    scriptReader.getShipClass(),
    DHServer.shipNames.random(),
    scriptReader.getShipClass().fuelTankSize.toDouble(),
    scriptReader.getStartingState(),
    null
) {

    override fun computeNextState(): ShipState {
        if (scriptReader.hasNextAction()) {
            if (scriptReader.nextActionShouldFire()) {
                receiveInputChange(scriptReader.getNextAction())
            }
        } else {
            //println("AI ship $uuid completed run to ${scriptReader.getDestinationStation().name} and will be removed.")
            ShipManager.removeShip(this)
        }
        return super.computeNextState() //will apply the inputs.
    }
}