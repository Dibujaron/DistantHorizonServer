package com.dibujaron.distanthorizon.ship.controller

import com.dibujaron.distanthorizon.navigation.NavigationRoute
import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.ship.IndexedState
import com.dibujaron.distanthorizon.ship.ShipInputs
import com.dibujaron.distanthorizon.ship.ShipManager
import com.dibujaron.distanthorizon.ship.ShipState
import java.lang.IllegalStateException
import kotlin.math.roundToInt

class AIShipController : ShipController() {

    var nextDepartureTime = System.currentTimeMillis()
    var currentRoute: NavigationRoute? = null

    override fun dockedTick(delta: Double) {
        if (System.currentTimeMillis() > nextDepartureTime) {
            plotNewCourse()
            ship.undock()
        }
    }

    fun plotNewCourse() {
        val stations = OrbiterManager.getStations().asSequence().filter {
            !ship.isDocked() || ship.dockedToPort!!.station != it
        }.filter{ it.getStar().name == "S-Regalis" }.toList()
        val destStation = stations.random()
        val destPort = destStation.dockingPorts.random()
        val myPort = ship.myDockingPorts.random()
        val newRoute = NavigationRoute(ship, myPort, destPort)
        println("AI ship ${ship.uuid} departing on route to ${destPort.station.displayName}")
        currentRoute = newRoute
    }

    fun dock() {
        ship.attemptDock()
        if (ship.isDocked()) {
            nextDepartureTime = System.currentTimeMillis() + 5000 + (Math.random() * 1000).roundToInt()
            val waitTime = nextDepartureTime - System.currentTimeMillis()
            println("AI ship ${ship.uuid} successfully docked at ${ship.dockedToPort!!.station.displayName}, will depart again after ${waitTime}ms")
        } else {
            println("AI ship ${ship.uuid} should have docked but failed to dock, removing ship.")
            ShipManager.markForRemove(ship)
        }
    }

    override fun computeNextState(delta: Double): ShipState {
        val route = currentRoute
        if (route != null && route.hasNext()) {
            return route.next()
        } else {
            dock()
            return ship.currentState
        }
    }

    override fun getCurrentControls(): ShipInputs {
        return ShipInputs()
    }

    override fun publishScript(numSteps: Int): Sequence<IndexedState> {
        val route = currentRoute
        if (route != null && route.hasNext()) {
            return route.publishScript(numSteps)
        } else {
            return emptySequence()
        }
    }

    override fun getCurrentStep(): Int {
        val route = currentRoute
        if (route != null && route.hasNext()) {
            return route.currentStep()
        } else {
            return 0
        }
    }

}