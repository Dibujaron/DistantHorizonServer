package com.dibujaron.distanthorizon.ship

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.database.persistence.ActorInfo
import com.dibujaron.distanthorizon.database.persistence.ShipInfo
import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.database.script.ScriptWriter
import com.dibujaron.distanthorizon.docking.DockingPort
import com.dibujaron.distanthorizon.docking.ShipDockingPort
import com.dibujaron.distanthorizon.docking.StationDockingPort
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.orbiter.Planet
import com.dibujaron.distanthorizon.player.Player
import com.dibujaron.distanthorizon.player.PlayerManager
import com.dibujaron.distanthorizon.player.wallet.Wallet
import org.json.JSONObject
import java.util.*
import kotlin.math.min
import kotlin.math.pow

open class Ship(
    val dbHook: ShipInfo?,
    val type: ShipClass,
    val name: String,
    private val colorScheme: ColorScheme,
    private val hold: MutableMap<CommodityType, Int>,
    private val passengers: MutableList<EmbarkedPassengerGroup>,
    var fuelLevel: Double,
    initialState: ShipState,
    val pilot: Player?
) {

    constructor(
        dbHook: ShipInfo?,
        type: ShipClass,
        name: String,
        fuelLevel: Double,
        initialState: ShipState,
        pilot: Player?
    ) : this(
        dbHook,
        type,
        name,
        type.getGoodColorScheme(),
        type.generateRandomHoldMap(),
        LinkedList(),
        fuelLevel,
        initialState,
        pilot
    )

    var currentState: ShipState = initialState
    val uuid = UUID.randomUUID()

    //controls
    val myDockingPorts = type.dockingPorts.asSequence().map { ShipDockingPort(this, it) }.toList()
    var dockedToPort: StationDockingPort? = null
    var myDockedPort: ShipDockingPort? = null

    var holdCapacity = type.holdSize

    var scriptWriter: ScriptWriter? = null

    fun holdOccupied(): Int {
        return hold.values.asSequence().sum()
    }

    fun getHoldQuantity(ct: CommodityType): Int {
        return hold[ct] ?: 0
    }

    fun updateHoldQuantity(ct: CommodityType, delta: Int) {
        val existing = hold[ct] ?: 0
        val newAmt = existing + delta
        hold[ct] = newAmt
        if (dbHook != null) {
            DHServer.getDatabase().getPersistenceDatabase().updateShipHold(dbHook, ct, newAmt)
            //todo some form of verification that we're still in sync.
        }
    }

    fun updateFuelLevel(delta: Double) {
        val newLevel = fuelLevel + delta
        println("Bought $delta fuel, level was $fuelLevel, new level is $newLevel")
        fuelLevel = newLevel
        if (dbHook != null) {
            DHServer.getDatabase().getPersistenceDatabase().updateShipFuelLevel(dbHook, fuelLevel)
        }
    }

    fun isDocked(): Boolean {
        return dockedToPort != null && myDockedPort != null
    }

    fun createHoldStatusMessage(): JSONObject {
        val retval = JSONObject()
        CommodityType.values().asSequence()
            .map { Pair(it.identifyingName, hold[it] ?: 0) }
            .forEach { retval.put(it.first, it.second) }
        return retval
    }

    var tickCount = 0

    fun tick() {
        val dockedTo = dockedToPort
        val dockedFrom = myDockedPort
        if (controls.mainEnginesActive && dockedTo == null && dockedFrom == null) {
            fuelLevel = if (fuelLevel < type.fuelBurnRatePerTick) 0.0 else fuelLevel - type.fuelBurnRatePerTick
        }
        currentState = if (dockedTo != null && dockedFrom != null) {
            val velocity = dockedTo.getVelocity()
            val myPortRelative = dockedFrom.relativePosition()
            val rotation = dockedTo.globalRotation() + dockedFrom.relativeRotation()
            val globalPos = dockedTo.globalPosition() + (myPortRelative * -1.0).rotated(rotation)
            ShipState(globalPos, rotation, velocity)
        } else {
            computeNextState()
        }
        tickCount++
    }

    private var controls: ShipInputs = ShipInputs()
    open fun computeNextState(): ShipState {
        val delta = DHServer.TICK_LENGTH_SECONDS
        var velocity = currentState.velocity
        var globalPos = currentState.position
        var rotation = currentState.rotation
        //velocity
        if (fuelLevel > 0) {
            if (controls.mainEnginesActive) {
                velocity += Vector2(0, -type.mainThrust).rotated(rotation) * delta
            }
        }
        if (controls.stbdThrustersActive) {
            velocity += Vector2(-type.manuThrust, 0).rotated(rotation) * delta
        }
        if (controls.portThrustersActive) {
            velocity += Vector2(type.manuThrust, 0).rotated(rotation) * delta
        }
        if (controls.foreThrustersActive) {
            velocity += Vector2(0, type.manuThrust).rotated(rotation) * delta
        }
        if (controls.aftThrustersActive) {
            velocity += Vector2(0, -type.manuThrust).rotated(rotation) * delta
        }
        //rotation
        if (controls.tillerLeft) {
            rotation -= type.rotationPower * delta
        } else if (controls.tillerRight) {
            rotation += type.rotationPower * delta
        }

        val gravityAccel = OrbiterManager.calculateGravityAtTick(0.0, globalPos) * delta
        velocity += gravityAccel
        globalPos += velocity * delta
        return ShipState(globalPos, rotation, velocity)
    }

    fun createFullShipJSON(): JSONObject {
        val retval = createShipHeartbeatJSON()
        retval.put("type", type.qualifiedName)
        retval.put("ship_name", name)
        retval.put("ship_model", type.displayName)
        retval.put("ship_make", type.manufacturer.displayNameShort)
        retval.put("hold_size", type.holdSize)
        retval.put("main_engine_thrust", type.mainThrust)
        retval.put("manu_engine_thrust", type.manuThrust)
        retval.put("rotation_power", type.rotationPower)
        retval.put("fuel_tank_size", type.fuelTankSize)
        retval.put("primary_color", colorScheme.primaryColor.toJSON())
        retval.put("secondary_color", colorScheme.secondaryColor.toJSON())
        retval.put("docking_ports", myDockingPorts.asSequence().map { it.toJSON() }.toList())
        retval.put("docked", isDocked())
        if (isDocked()) {
            retval.put("docked_info", createDockedMessage())
        }
        return retval
    }

    fun createShipHeartbeatJSON(): JSONObject {
        val retval = JSONObject()
        retval.put("id", uuid)
        retval.put("velocity", currentState.velocity.toJSON())
        retval.put("global_pos", currentState.position.toJSON())
        retval.put("rotation", currentState.rotation)
        retval.put("hold_occupied", holdOccupied())
        retval.put("main_engines", controls.mainEnginesActive)
        retval.put("port_thrusters", controls.portThrustersActive)
        retval.put("stbd_thrusters", controls.stbdThrustersActive)
        retval.put("fore_thrusters", controls.foreThrustersActive)
        retval.put("aft_thrusters", controls.aftThrustersActive)
        retval.put("rotating_left", controls.tillerLeft)
        retval.put("rotating_right", controls.tillerRight)
        retval.put("fuel_level", fuelLevel)
        return retval
    }

    fun attemptDock(
        maxDockDist: Double = DHServer.dockingDist,
        maxClosingSpeed: Double = DHServer.dockingSpeed
    ): DockingResult {
        val maxDistSquared = maxDockDist.pow(2)
        val maxClosingSpeedSquared = maxClosingSpeed.pow(2)

        var anyPassedDistTest = false
        val match = OrbiterManager.getStations().asSequence()
            .flatMap { it.dockingPorts.asSequence() }
            .flatMap { stationPort ->
                myDockingPorts.asSequence()
                    .map { shipPort -> Pair(shipPort, stationPort) }
            }
            .filter { it.first.relativeRotation() + it.second.relativeRotation == 0.0 } //only want docking position facing forward
            .map { Triple(it.first, it.second, (it.first.globalPosition() - it.second.globalPosition()).lengthSquared) }
            .filter { it.third < maxDistSquared }
            .onEach { anyPassedDistTest = true }
            .filter { (it.first.getVelocity() - it.second.getVelocity()).lengthSquared < maxClosingSpeedSquared }
            .minByOrNull { it.third }

        return if (match != null) {
            val bestShipPort = match.first
            val bestStationPort = match.second
            dock(bestShipPort, bestStationPort)
            DockingResult.SUCCESS
        } else {
            if (anyPassedDistTest) {
                DockingResult.CLOSING_SPEED_TOO_GREAT
            } else {
                DockingResult.DISTANCE_TOO_GREAT
            }
        }
    }

    fun dock(shipPort: ShipDockingPort, stationPort: StationDockingPort, updateLastDocked: Boolean = true) {
        this.myDockedPort = shipPort
        this.dockedToPort = stationPort
        DHServer.broadcastShipDocked(this)
        BackgroundTaskManager.executeInBackground {
            val database = DHServer.getDatabase()
            val writer = scriptWriter
            if (writer != null) {
                val routeKey = writer.completeScript(stationPort.station.key)
                val scriptDatabase = database.getScriptDatabase()
                val script = scriptDatabase.selectScriptByKey(routeKey)
                stationPort.station.asyncAddScript(script)
            }
            val persistenceDatabase = database.getPersistenceDatabase()
            pilot?.actorInfo?.let {
                if (updateLastDocked) {
                    persistenceDatabase.updateActorDockedStation(it, stationPort.station.key)
                    println("Updated last docked station of ${pilot.actorInfo?.displayName} to ${stationPort.station.name}")
                }
            }
            if (dbHook != null && updateLastDocked) {
                persistenceDatabase.updateShipFuelLevel(dbHook, fuelLevel)
            }
        }
    }

    fun createDockedMessage(): JSONObject {
        val myPort: DockingPort? = this.myDockedPort
        val stationPort: StationDockingPort? = this.dockedToPort;
        if (myPort == null || stationPort == null) {
            throw IllegalStateException("Creating docked message but not docked.")
        } else {
            val dockedMessage = JSONObject()
            dockedMessage.put("id", uuid)
            dockedMessage.put("station_identifying_name", stationPort.station.name)
            dockedMessage.put("ship_port", myPort.toJSON())
            dockedMessage.put("station_port", stationPort.toJSON())
            return dockedMessage
        }
    }

    fun undock() {
        val dockedTo = dockedToPort
        if (dockedTo != null) {
            DHServer.broadcastShipUndocked(this)
            if (pilot != null) {
                scriptWriter = DHServer.getDatabase()
                    .getScriptDatabase()
                    .beginLoggingScript(pilot.actorInfo, dockedTo.station.key, currentState, type)
            }
        }
        dockedToPort = null
        myDockedPort = null
    }

    fun receiveInputChange(newInputs: ShipInputs) {
        controls = newInputs
        scriptWriter?.writeAction(newInputs)
        broadcastInputsChange()
    }

    private fun broadcastInputsChange() {
        val inputsUpdate = createShipHeartbeatJSON()
        inputsUpdate.put("main_engines", controls.mainEnginesActive)
        inputsUpdate.put("port_thrusters", controls.portThrustersActive)
        inputsUpdate.put("stbd_thrusters", controls.stbdThrustersActive)
        inputsUpdate.put("fore_thrusters", controls.foreThrustersActive)
        inputsUpdate.put("aft_thrusters", controls.aftThrustersActive)
        inputsUpdate.put("rotating_left", controls.tillerLeft)
        inputsUpdate.put("rotating_right", controls.tillerRight)
        PlayerManager.getPlayers().asSequence()
            .forEach { it.queueInputsUpdateMsg(inputsUpdate) }
    }

    fun buyResourceFromStation(commodity: CommodityType, purchasingWallet: Wallet, quantity: Int) {
        if (isDocked()) {
            val station = dockedToPort!!.station
            station.sellResourceToShip(commodity, purchasingWallet, this, quantity)
        }
    }


    fun sellResourceToStation(commodity: CommodityType, purchasingWallet: Wallet, quantity: Int) {
        if (isDocked()) {
            val station = dockedToPort!!.station
            station.buyResourceFromShip(commodity, purchasingWallet, this, quantity)
        }
    }

    fun purchaseFuelFromStation(purchasingWallet: Wallet, quantity: Int) {
        if (isDocked()) {
            val station = dockedToPort!!.station
            station.sellFuelToShip(purchasingWallet, this, quantity)
        }
    }


    fun loadPassengers(destStation: StationKey, loadQuantity: Int) {
        if (isDocked()) {
            val station = dockedToPort!!.station
            val waitingRoom = station.waitingRoom
            val waitingPassengers = waitingRoom.waitingPassengers
            val existingQuantity = waitingPassengers[destStation] ?: 0
            val newLoadQuantity = min(existingQuantity, loadQuantity)
            val newWaitingQuantity = existingQuantity - newLoadQuantity
            waitingPassengers[destStation] = newWaitingQuantity
            val embarkLocation = currentState.position
            val embarkTime = System.currentTimeMillis()
            val embarkedPassengerGroup = EmbarkedPassengerGroup(
                station.key,
                embarkLocation,
                embarkTime,
                destStation,
                newLoadQuantity
            )
            passengers.add(embarkedPassengerGroup)
            val hook = dbHook
            if (hook != null) {
                BackgroundTaskManager.executeInBackground {
                    DHServer.getDatabase().getPersistenceDatabase()
                        .movePassengersFromStationToShip(
                            hook,
                            station.key,
                            destStation,
                            embarkLocation,
                            embarkTime,
                            newLoadQuantity
                        )
                }
            }
        }
    }

    companion object {
        fun createGuestShip(player: Player): Ship {
            val shipClass = ShipClassManager.getShipClass(DHServer.playerStartingShip)!!
            val colors = shipClass.getGoodColorScheme()
            val fuelLevel = shipClass.fuelTankSize
            return Ship(
                null,
                shipClass,
                DHServer.shipNames.random(),
                colors,
                EnumMap(CommodityType::class.java),
                LinkedList(),
                fuelLevel.toDouble(),
                getStartingOrbit(),
                player
            )
        }

        fun createFromSave(player: Player, actorInfo: ActorInfo): Ship {
            val savedShip = actorInfo.ship
            val embarkedPassengers = DHServer.getDatabase().getPersistenceDatabase().getPassengersOnShip(savedShip);
            return Ship(
                savedShip,
                savedShip.shipClass,
                savedShip.name,
                ColorScheme(savedShip.primaryColor, savedShip.secondaryColor),
                savedShip.holdMap.toMutableMap(),
                embarkedPassengers.toMutableList(),
                savedShip.fuelLevel,
                getStartingOrbit(),
                player
            )
        }

        private fun getStartingOrbit(): ShipState {
            val startingPlanetName = DHServer.startingPlanetName
            val startingPlanet: Planet = OrbiterManager.getPlanet(startingPlanetName)
                ?: throw IllegalArgumentException("starting planet $startingPlanetName is null.")
            val offset = Vector2(-DHServer.startingOrbitalRadius, 0)
            val planetPos = startingPlanet.globalPos()
            val startingPos = planetPos + offset
            val startingVelocity = Vector2(DHServer.startingOrbitalSpeed, 0)
            val rotation = 0.0
            return ShipState(startingPos, rotation, startingVelocity)
        }
    }
}

