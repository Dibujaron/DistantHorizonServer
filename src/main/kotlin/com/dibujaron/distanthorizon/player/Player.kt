package com.dibujaron.distanthorizon.player

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.command.CommandSender
import com.dibujaron.distanthorizon.command.Permission
import com.dibujaron.distanthorizon.database.persistence.AccountInfo
import com.dibujaron.distanthorizon.database.persistence.ActorInfo
import com.dibujaron.distanthorizon.database.persistence.ShipInfo
import com.dibujaron.distanthorizon.event.EventManager
import com.dibujaron.distanthorizon.event.PlayerChatEvent
import com.dibujaron.distanthorizon.login.PendingLoginManager
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.player.wallet.AccountWallet
import com.dibujaron.distanthorizon.player.wallet.GuestWallet
import com.dibujaron.distanthorizon.player.wallet.Wallet
import com.dibujaron.distanthorizon.ship.*
import io.javalin.websocket.WsContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class Player(val connection: WsContext) : CommandSender {
    var accountInfo: AccountInfo? = null
    var actorInfo: ActorInfo? = null
    private val companionAI: PlayerCompanionAI = PlayerCompanionAI(this)
    lateinit var ship: Ship
    private val incomingMessageQueue: Queue<JSONObject> = LinkedList()
    private val outgoingMessageQueue: Queue<JSONObject> = LinkedList()
    var initialized: Boolean = false
    lateinit var wallet: Wallet

    override fun sendMessage(message: String) {
        queueChatMsg(message)
    }

    override fun hasPermission(permission: Permission): Boolean {
        return accountInfo?.accountName == "dibujaron0511"
    }


    private fun processClientFirstMessage(message: JSONObject) {
        println("processing client opening message.")
        val authenticationExpected = message.getBoolean("authenticated")
        if (authenticationExpected) {
            println("authentication expected.")
            val clientKey = message.getString("client_key")
            val username = PendingLoginManager.completeLogin(clientKey)
            if (username != null) {
                println("Username is $username, expecting actor name.")
                val actorID = message.getInt("actor_id")
                val db = DHServer.getDatabase().getPersistenceDatabase()
                val myAccount = db.selectOrCreateAccount(username)
                var myActor: ActorInfo
                if (username == "Debug0000") {
                    myActor = if (myAccount.actors.isEmpty()) {
                        println("debug user has no actors, creating new.")
                        val newAcct = db.createNewActorForAccount(myAccount, "debug")
                        newAcct!!.actors.first()
                    } else {
                        println("user is debug, returning debug user.")
                        myAccount.actors.first()
                    }
                } else {
                    myActor = myAccount.actors.find { it.uniqueID == actorID }!!
                }
                println("actor name is ${myActor.displayName}")
                accountInfo = myAccount
                actorInfo = myActor
                wallet = AccountWallet(myActor)
                ship = Ship.createFromSave(this, myActor)

            } else {
                queueShipAIChatMsg("ERROR: client authentication expected, but failed. Please report this to the DH team.")
            }
        } else {
            println("authentication not expected, proceeding as guest.")
            ship = Ship.createGuestShip(this)
            wallet = GuestWallet()
        }

        queueChatMsg("... Shipboard artificial intelligence is loading ...")
        queueShipAIChatMsg(companionAI.getInitializationMessage())

        val myAccount = accountInfo
        if (myAccount != null) {
            queueShipAIChatMsg(companionAI.getLoggedInGreeting())
            PlayerManager.mapAuthenticatedPlayer(myAccount.accountName, this)
        } else {
            queueShipAIChatMsg(companionAI.getGuestGreeting())
            queueShipAIChatMsg("Warning: Because you are playing as a guest, your progress will be lost if this tab is closed.")
        }

        ShipManager.addShip(ship)
        val worldStateMessage = DHServer.composeWorldStateMessage()
        val shipsMessage = DHServer.composeMessageForShipsAdded(ShipManager.getShips())
        queueWorldStateMsg(worldStateMessage)
        queueShipsAddedMsg(shipsMessage)

        val myActor = actorInfo
        initialized = true
        if (myActor?.lastDockedStation != null) {
            val lastStation = OrbiterManager.getStationByKeyRequired(myActor.lastDockedStation);
            ship.dock(ship.myDockingPorts.random(), lastStation.dockingPorts.random(), false)
            queueSendStationMenuMessage()
        }
    }

    fun isAuthenticated(): Boolean {
        return accountInfo != null
    }

    fun getUsername(): String {
        return accountInfo?.accountName ?: "guest"
    }

    fun queueIncomingMessageFromClient(message: JSONObject) {
        if (DHServer.REQUEST_BATCHING) {
            incomingMessageQueue.add(message)
        } else {
            processIncomingMessage(message)
        }
    }

    fun tick() {
        while (!incomingMessageQueue.isEmpty()) {
            processIncomingMessage(incomingMessageQueue.remove())
        }
        while (!outgoingMessageQueue.isEmpty()) {
            sendMessage(outgoingMessageQueue.remove())
        }
    }

    private fun processIncomingMessage(message: JSONObject) {
        //todo refactor - separate handlers for each message type?
        val messageType = message.getString("message_type")
        if (messageType == "init") {
            processClientFirstMessage(message)
        }
        if (messageType == "ship_inputs") {
            val inputs = ShipInputs(message)
            ship.receiveInputChange(inputs)
        } else if (messageType == "dock") {
            if (!ship.isDocked()) {
                when (ship.attemptDock()) {
                    DockingResult.SUCCESS -> queueSendStationMenuMessage()
                    DockingResult.DISTANCE_TOO_GREAT -> queueShipAIChatMsg("We cannot dock; no stations are within docking range.")
                    DockingResult.CLOSING_SPEED_TOO_GREAT -> queueShipAIChatMsg("Our relative velocity is too high for the docking magnets to safely engage.")
                }
            }
        } else if (messageType == "undock") {
            if (ship.isDocked()) {
                ship.undock()
                queueSendStationMenuMessage()
            } else {
                queueChatMsg("Ship is not docked?")
            }
        } else if (messageType == "purchase_from_station") {
            if (ship.isDocked()) {
                val commodity = CommodityType.fromString(message.getString("commodity_name"))
                val quantity = message.getInt("quantity")
                ship.buyResourceFromStation(commodity, wallet, quantity)
                println("${getDisplayName()} bought $quantity of $commodity from station, new balance is ${wallet.getBalance()}")
                queueSendStationMenuMessage()
            }
        } else if (messageType == "sell_to_station") {
            if (ship.isDocked()) {
                val commodity = CommodityType.fromString(message.getString("commodity_name"))
                val quantity = message.getInt("quantity")
                ship.sellResourceToStation(commodity, wallet, quantity)
                println("${getDisplayName()} sold $quantity of $commodity from station, new balance is ${wallet.getBalance()}")
                queueSendStationMenuMessage()
            }
        } else if (messageType == "purchase_fuel") {
            if(ship.isDocked()){
                val quantity = message.getInt("quantity")
                ship.purchaseFuelFromStation(wallet, quantity)
                queueSendStationMenuMessage()
            }
        } else if (messageType == "chat") {
            val payload = message.getString("payload")
            val event = PlayerChatEvent(this, payload)
            EventManager.triggerPlayerChatEvent(event)
            if (!event.cancelled) {
                PlayerManager.broadcast(event.player.getDisplayName(), event.message)
            }
        } else if (messageType == "buy_ship") {
            println("got buy ship message")
            val qualName = message.getString("ship_class_qualified_name")
            val name = message.getString("ship_name")
            val color1 = ShipColor.fromJSON(message.getJSONObject("primary_color"))
            val color2 = ShipColor.fromJSON(message.getJSONObject("secondary_color"))
            val newShipClass = ShipClassManager.getShipClass(qualName)!!
            val oldShipClass = ship.type
            var cost = newShipClass.getPurchaseCost(oldShipClass)
            val newBalance = wallet.getBalance() - cost
            if (newBalance > 0) {
                wallet.setBalance(newBalance)
                updateShip(ShipClassManager.getShipClass(qualName)!!, name, ColorScheme(color1, color2))
            }
            queueSendStationMenuMessage()
        }
    }

    fun updateShip(shipClass: ShipClass, name: String, colorScheme: ColorScheme) {
        println("updating ship")
        val actor = actorInfo
        var dbHook: ShipInfo? = null
        if (actor != null) {
            println("updating ship in database.")
            BackgroundTaskManager.executeInBackground {
                val newActor = DHServer.getDatabase().getPersistenceDatabase()
                    .updateShipOfActor(actor, shipClass, name, colorScheme, shipClass.fuelTankSize.toDouble())
                dbHook = newActor?.ship
            }
        }
        val newHold: MutableMap<CommodityType, Int> = EnumMap(CommodityType::class.java)
        val newPassengers: MutableList<EmbarkedPassengerGroup> = LinkedList()
        val newShip = Ship(dbHook, shipClass, name, colorScheme, newHold, newPassengers, shipClass.fuelTankSize.toDouble(), ship.currentState, this)
        val oldShip = ship
        ship = newShip
        //newShip.currentState = oldShip.currentState //todo why was this done here? originally ship.currentState above was getStartingOrbit()
        ShipManager.addShip(newShip)
        newShip.dock(newShip.myDockingPorts.random(), oldShip.dockedToPort!!)
        ShipManager.removeShip(oldShip)
        println("ship update complete.")
    }

    fun getDisplayName(): String {
        return actorInfo?.displayName ?: "Guest";
    }

    private fun queueSendStationMenuMessage() {
        val dockedTo = ship.dockedToPort
        if (dockedTo != null) {
            val dockedToStation = dockedTo.station
            val stationInfo = dockedToStation.createShopMessage(this)
            val myMessage = createMessage("station_menu_info")
            myMessage.put("station_info", stationInfo)
            myMessage.put("player_balance", wallet.getBalance())
            myMessage.put("hold_space", ship.holdCapacity - ship.holdOccupied())
            myMessage.put("fuel_tank_size", ship.type.fuelTankSize)
            myMessage.put("fuel_level", ship.fuelLevel)
            val holdInfo: JSONObject = ship.createHoldStatusMessage()
            myMessage.put("hold_contents", holdInfo)
            queueMessage(myMessage)
        } else {
            val myMessage = createMessage("station_menu_close")
            queueMessage(myMessage)
        }
    }

    fun queueWorldStateMsg(worldStateMessage: JSONObject) {
        val myMessage = createMessage("world_state")
        myMessage.put("world_state", worldStateMessage)
        queueMessage(myMessage)
    }

    fun queueShipDockedMsg(shipDockedMessage: JSONObject) {
        val myMessage = createMessage("ship_docked")
        myMessage.put("ship_docked", shipDockedMessage);
        queueMessage(myMessage)
    }

    fun queueShipUndockedMsg(shipUndockedMessage: JSONObject) {
        val myMessage = createMessage("ship_undocked")
        myMessage.put("ship_undocked", shipUndockedMessage);
        queueMessage(myMessage)
    }

    fun queueInputsUpdateMsg(shipInputsUpdate: JSONObject) {
        val myMessage = createMessage("ship_inputs")
        myMessage.put("ship_inputs", shipInputsUpdate)
        queueMessage(myMessage)
    }

    fun queueShipHeartbeatsMsg(shipHeartbeats: JSONArray) {
        val myMessage = createMessage("ship_heartbeats")
        myMessage.put("ship_heartbeats", shipHeartbeats)
        queueMessage(myMessage)
    }

    fun queueShipsAddedMsg(shipsAdded: JSONArray) {
        val myMessage = createMessage("ships_added")
        myMessage.put("ships_added", shipsAdded)
        queueMessage(myMessage)
    }

    fun queueShipsRemovedMsg(shipsRemoved: JSONArray) {
        val myMessage = createMessage("ships_removed")
        myMessage.put("ships_removed", shipsRemoved)
        queueMessage(myMessage)
    }

    fun queueShipAIChatMsg(message: String) {
        queueChatMsg("[${companionAI.getName()}]", message)
    }

    fun sendServerMessageImmediate(message: String) {
        println("sending immediate")
        val myMessage = createMessage("chat")
        myMessage.put("payload", "ATTENTION: $message")
        sendMessage(myMessage)
    }

    fun queueChatMsg(senderName: String, message: String) {
        queueChatMsg("$senderName: $message")
    }

    fun queueChatMsg(message: String) {
        val myMessage = createMessage("chat")
        myMessage.put("payload", message)
        queueMessage(myMessage)
    }

    fun createMessage(type: String): JSONObject {
        val message = JSONObject();
        message.put("message_type", type)
        message.put("ship_id", ship.uuid.toString())
        return message
    }

    fun queueMessage(message: JSONObject) {
        if (DHServer.REQUEST_BATCHING) {
            outgoingMessageQueue.add(message)
        } else {
            sendMessage(message)
        }
    }

    fun sendMessage(message: JSONObject) {
        val messageStr = message.toString()
        connection.send(messageStr)
    }

}