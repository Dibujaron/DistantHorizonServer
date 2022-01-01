package com.dibujaron.distanthorizon.orbiter.station

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.database.script.ScriptReader
import com.dibujaron.distanthorizon.docking.StationDockingPort
import com.dibujaron.distanthorizon.orbiter.*
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.orbiter.station.hold.fixed.FixedValueStationHold
import com.dibujaron.distanthorizon.orbiter.station.hold.StationHold
import com.dibujaron.distanthorizon.orbiter.station.hold.dynamic.DynamicEconomyManager
import com.dibujaron.distanthorizon.orbiter.station.hold.dynamic.DynamicStationHold
import com.dibujaron.distanthorizon.orbiter.station.passenger.StationPassengerWaitingRoom
import com.dibujaron.distanthorizon.player.Player
import com.dibujaron.distanthorizon.player.wallet.Wallet
import com.dibujaron.distanthorizon.ship.*
import com.dibujaron.distanthorizon.utils.TimeUtils
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random

class Station(parentName: String?, stationName: String, properties: Properties) :
    Orbiter(parentName, stationName, properties) {

    val key = DHServer.getDatabase().getPersistenceDatabase().selectOrCreateStation(stationName, properties)
    val dockingPorts = LinkedList<StationDockingPort>()
    private val splashTextList = ArrayList<String>()
    private val dealerships = HashMap<Manufacturer, Int>()
    private val navigable = properties.getProperty("navigable", "true").toBoolean()
    private val fuelPrice = properties.getProperty("fuel.price").toDouble()
    private var aiScripts: MutableMap<Int, MutableSet<ScriptReader>> = ConcurrentHashMap()

    private val hold: StationHold =
        if (DynamicEconomyManager.isEnabled())
            DynamicStationHold(key, properties)
        else FixedValueStationHold(properties)

    val waitingRoom: StationPassengerWaitingRoom = StationPassengerWaitingRoom(key, properties)

    init {
        dockingPorts.add(StationDockingPort(this, Vector2(7.0, 0.5), -90.0))
        dockingPorts.add(StationDockingPort(this, Vector2(-7.0, 0.5), 90.0))
        var index = 0
        var currentSplash = properties.getProperty("splash.$index", null)
        while (currentSplash != null) {
            splashTextList.add(currentSplash)
            index++
            currentSplash = properties.getProperty("splash.$index", null)
        }
        Manufacturer.values().forEach {
            val dealershipPercentage = properties.getProperty("dealership.${it.identifyingName}", "0").toInt()
            if (dealershipPercentage > 0) {
                dealerships[it] = dealershipPercentage
            }
        }
    }

    fun initAiScripts() {
        if (this.navigable) {
            DHServer.getDatabase().getScriptDatabase()
                .selectScriptsForStation(this.key).asSequence()
                .filter { OrbiterManager.getStationByKeyRequired(it.getDestinationStation()).navigable }
                .forEach { aiScripts.getOrPut(it.getDepartureTick()) { mutableSetOf() }.add(it) }
        }
    }

    fun asyncAddScript(it: ScriptReader) {
        if (this.navigable) {
            aiScripts.getOrPut(it.getDepartureTick()) { mutableSetOf() }.add(it)
        }
    }

    override fun tick() {
        super.tick()
        hold.tick()
        waitingRoom.tick()
        aiScripts.getOrElse(TimeUtils.getCurrentTickInCycle()) { setOf() }.forEach {
            ShipManager.addShip(AIShip(it.copy()))
        }

    }

    fun globalRotation(): Double {
        val vecToParent = relativePos * -1.0
        return vecToParent.angle
    }

    fun createShopMessage(player: Player): JSONObject {
        val changesEveryFiveMinutes = System.currentTimeMillis() / 300000
        val rand = Random(changesEveryFiveMinutes + displayName.hashCode())
        val retval = JSONObject()
        retval.put("identifying_name", name)
        retval.put("display_name", displayName)
        retval.put("description", splashTextList.random(rand))
        retval.put("fuel_price", fuelPrice)
        retval.put("commodity_stores", hold.toJSON())
        val dealershipJson = JSONArray()
        Manufacturer.values().forEach {
            val percent = dealerships[it]
            if (percent != null) {
                val json = it.toJSON(player, rand, percent)
                if (!json.getJSONArray("ship_classes").isEmpty) {
                    dealershipJson.put(json)
                }
            }
        }
        retval.put("dealerships", dealershipJson)
        return retval
    }

    fun sellResourceToShip(resource: CommodityType, buyingWallet: Wallet, ship: Ship, quantity: Int) {
        val store = hold.getCommodityStore(resource)
        var purchaseQuantity = quantity

        //first check if there's enough on the station
        val availableQuantity = store.getQuantityAvailable()
        if (purchaseQuantity > availableQuantity) {
            purchaseQuantity = availableQuantity
        }

        //now check if there's room in the hold
        val spaceInHold = ship.holdCapacity - ship.holdOccupied()
        if (purchaseQuantity > spaceInHold) {
            purchaseQuantity = spaceInHold
        }
        var price = store.getPrice() * purchaseQuantity
        if (buyingWallet.getBalance() < price) {
            val affordableQuantity = buyingWallet.getBalance() / store.getPrice()
            purchaseQuantity = affordableQuantity
        }
        val purchasePrice = store.getPrice() * purchaseQuantity
        buyingWallet.setBalance(buyingWallet.getBalance() - purchasePrice)
        store.updateQuantityAvailable(-purchaseQuantity)
        ship.updateHoldQuantity(resource, purchaseQuantity)
    }

    fun sellFuelToShip(buyingWallet: Wallet, ship: Ship, quantity: Int) {
        val spaceInTank = ship.type.fuelTankSize - ship.fuelLevel
        var purchaseQuantity = quantity.toDouble()
        if (purchaseQuantity > spaceInTank) {
            purchaseQuantity = spaceInTank
        }
        val price = ceil((fuelPrice * purchaseQuantity)).toInt()
        if (buyingWallet.getBalance() < price) {
            val affordableQuantity = floor((buyingWallet.getBalance() / fuelPrice))
            purchaseQuantity = affordableQuantity
        }
        val purchasePrice = ceil((fuelPrice * purchaseQuantity)).toInt()
        buyingWallet.setBalance(buyingWallet.getBalance() - purchasePrice)
        ship.updateFuelLevel(purchaseQuantity)
    }

    fun buyResourceFromShip(resource: CommodityType, buyingWallet: Wallet, ship: Ship, quantity: Int) {
        val store = hold.getCommodityStore(resource)
        var purchaseQuantity = quantity

        //first check if there's enough in player's hold to sell
        val availableQuantity = ship.getHoldQuantity(resource)
        if (purchaseQuantity > availableQuantity) {
            purchaseQuantity = availableQuantity
        }

        //now do it
        val purchasePrice = store.getPrice() * purchaseQuantity
        buyingWallet.setBalance(buyingWallet.getBalance() + purchasePrice)
        store.updateQuantityAvailable(purchaseQuantity)
        ship.updateHoldQuantity(resource, -purchaseQuantity)
    }

    override fun createOrbiterJson(): JSONObject {
        val retval = super.createOrbiterJson()
        retval.put("display_name", displayName)
        retval.put("navigable", navigable)
        return retval
    }

    fun writeEconomyCSV(builder: java.lang.StringBuilder) {
        builder.append("\"").append(displayName).append("\",")
        builder.append(
            CommodityType.values().asSequence()
                .map { hold.getCommodityStore(it) }
                .map { it.getPrice() }
                .map { if (it == 0) "" else it.toString() }
                .joinToString(","))
        builder.append("\n")
    }

    companion object {
        fun createEconomyCSV(): String {
            val builder = StringBuilder()
            builder.append("station,")
            builder.append(
                CommodityType.values().asSequence()
                    .map { it.displayName }
                    .joinToString(","))
            builder.append("\n")
            OrbiterManager.getStations().asSequence()
                .sortedBy { it.displayName }
                .forEach { it.writeEconomyCSV(builder) }
            return builder.toString()
        }
    }
}