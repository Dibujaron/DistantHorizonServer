package com.dibujaron.distanthorizon

import com.dibujaron.distanthorizon.background.BackgroundTaskManager
import com.dibujaron.distanthorizon.command.CommandManager
import com.dibujaron.distanthorizon.command.CommandSender
import com.dibujaron.distanthorizon.database.DHDatabase
import com.dibujaron.distanthorizon.database.impl.ExDatabase
import com.dibujaron.distanthorizon.discord.DiscordManager
import com.dibujaron.distanthorizon.login.PendingLoginManager
import com.dibujaron.distanthorizon.orbiter.OrbiterManager
import com.dibujaron.distanthorizon.orbiter.station.Station
import com.dibujaron.distanthorizon.orbiter.station.hold.dynamic.DynamicEconomyManager
import com.dibujaron.distanthorizon.passenger.PassengerManager
import com.dibujaron.distanthorizon.player.Player
import com.dibujaron.distanthorizon.player.PlayerManager
import com.dibujaron.distanthorizon.ship.Ship
import com.dibujaron.distanthorizon.ship.ShipManager
import io.javalin.Javalin
import io.javalin.websocket.WsBinaryMessageContext
import io.javalin.websocket.WsCloseContext
import io.javalin.websocket.WsConnectContext
import io.javalin.websocket.WsErrorContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.thread
import kotlin.math.ceil

fun main() {
    thread { DHServer.commandLoop() }
}

object DHServer {

    const val TICK_LENGTH_SECONDS = 1.0 / 60.0
    const val TICK_LENGTH_MILLIS = 1000.0 / 60.0
    val TICK_LENGTH_MILLIS_CEIL = ceil(TICK_LENGTH_MILLIS).toLong()
    const val TICKS_PER_SECOND = 60

    private var shuttingDown = false
    private val serverProperties: Properties = loadProperties()
    private val restartCommand: String = serverProperties.getProperty("restart.command", "")
    private val serverPort = serverProperties.getProperty("server.port", "25611").toInt()
    val serverSecret: String = serverProperties.getProperty("server.secret", "debug")
    val maxPlayers = serverProperties.getProperty("max.players", "60").toInt()
    private val shipHeartbeatsEvery = serverProperties.getProperty("heartbeats.ship", "60").toInt()
    private val shipHeartbeatsTickOffset = serverProperties.getProperty("heartbeats.ship.offset", "0").toInt()
    private val worldHeartbeatsEvery = serverProperties.getProperty("heartbeats.world", "10").toInt()
    private val worldHeartbeatsTickOffset = serverProperties.getProperty("heartbeats.world.offset", "0").toInt()
    val playerStartingShip: String = serverProperties.getProperty("starting.ship", "rijay.crusader")
    val startingPlanetName: String = serverProperties.getProperty("starting.planet", "Rakuri")
    val startingOrbitalRadius = serverProperties.getProperty("starting.radius", "400.0").toDouble()
    val startingOrbitalSpeed = serverProperties.getProperty("starting.speed", "25.0").toDouble()
    val dockingSpeed = serverProperties.getProperty("docking.speed", "200.0").toDouble()
    val dockingDist = serverProperties.getProperty("docking.distance", "200.0").toDouble()
    var debug = serverProperties.getProperty("debug", "true").toBoolean()
    val isMaster = serverProperties.getProperty("master", "true").toBoolean()
    val cycleLengthTicks = serverProperties.getProperty("cycle.length.ticks", "83160").toInt()
    val factorsOfCycleLengthTicks = factors(cycleLengthTicks)
    val requestBatching = serverProperties.getProperty("request.batching", "true").toBoolean()
    val startingBalance = serverProperties.getProperty("starting.balance", "1000").toInt()
    val dbUrl = serverProperties.getProperty(
        "database.url",
        "jdbc:mysql://root:admin@localhost:3306/distant_horizon"
    )
    val dbDriver = serverProperties.getProperty("database.driver", "com.mysql.cj.jdbc.Driver")
    private val database: DHDatabase = ExDatabase(dbUrl, dbDriver)

    val shipNames = loadShipNames()

    private val modules: List<DHModule> = listOf(
        CommandManager,
        DiscordManager,
        DynamicEconomyManager,
        BackgroundTaskManager,
        OrbiterManager,
        PlayerManager,
        PassengerManager,
        BalancerPingManager,
        ShipManager
    )

    init {
        modules.forEach { it.moduleInit(serverProperties) }
    }

    private val javalin = initJavalin(serverPort)
    val timer =
        fixedRateTimer(
            name = "mainThread",
            initialDelay = TICK_LENGTH_MILLIS_CEIL,
            period = TICK_LENGTH_MILLIS_CEIL
        ) { mainLoop() }

    private var tickCount = 0

    fun getDatabase(): DHDatabase {
        return database
    }

    fun getTickCount(): Int {
        return tickCount
    }

    fun commandLoop() {
        while (!shuttingDown) {
            val command = readLine()
            if (command != null && !CommandManager.handleConsoleCommand(command)) {
                println("Unknown command $command")
            }
        }
    }

    fun shutDown() {
        shuttingDown = true;
        timer.cancel()
        javalin.stop()
        modules.forEach { it.shutDown() }
    }

    fun restart(sender: CommandSender? = null) {
        sender?.sendMessage("Attempting to execute restart command...")
        if (restartCommand.isNotEmpty()) {
            Runtime.getRuntime().exec(restartCommand)
        }
        sender?.sendMessage("Command executed.")
    }

    var lastTickTime = System.currentTimeMillis()
    var accumulator = 0.0

    fun isShuttingDown(): Boolean {
        return shuttingDown
    }

    private fun mainLoop() {
        val tickTime = System.currentTimeMillis()
        val delta = tickTime - lastTickTime
        accumulator += delta
        while (accumulator >= TICK_LENGTH_MILLIS) {
            tick()
            accumulator -= TICK_LENGTH_MILLIS
        }
        lastTickTime = tickTime
    }

    private fun tick() {
        modules.forEach{it.tick()}

        val isWorldStateMessageTick = tickCount % worldHeartbeatsEvery == worldHeartbeatsTickOffset
        if (isWorldStateMessageTick) {
            val worldStateMessage = composeWorldStateMessage()
            PlayerManager.getPlayers()
                .filter { it.initialized }
                .forEach { it.queueWorldStateMsg(worldStateMessage) }
        }
        val isShipStateMessageTick = tickCount % shipHeartbeatsEvery == shipHeartbeatsTickOffset
        if (isShipStateMessageTick) {
            val shipHeartbeatsMessage = composeShipHeartbeatsMessageForAll()
            PlayerManager.getPlayers()
                .filter { it.initialized }
                .forEach { it.queueShipHeartbeatsMsg(shipHeartbeatsMessage) }
        }
        tickCount++
    }

    private fun initJavalin(port: Int): Javalin {
        println("initializing javalin on port $port")
        return Javalin.create { config ->
            config.defaultContentType = "application/json"
            config.autogenerateEtags = true
            config.asyncRequestTimeout = 10_000L
            config.enforceSsl = false
            config.showJavalinBanner = false
        }.ws("/ws/") { ws ->
            ws.onConnect { onClientConnect(it) }
            ws.onClose { onClientDisconnect(it) }
            ws.onBinaryMessage { onMessageReceived(it) }
            ws.onError { onSocketError(it) }
        }.get("/:serverSecret/prepLogin/:username") {
            if (verifySecret(it.pathParam("serverSecret"))) {
                val username = it.pathParam("username")
                val token = PendingLoginManager.registerPendingLoginGenerateToken(username)
                val db = database.getPersistenceDatabase()
                val acct = db.selectOrCreateAccount(username)
                val json = acct.toJSON()
                json.put("token", token)
                it.result(json.toString())
            }
        }.get("/:serverSecret/account/:accountName") {
            if (verifySecret(it.pathParam("serverSecret"))) {
                val acctName = it.pathParam("accountName")
                val dbInfo = database.getPersistenceDatabase().selectOrCreateAccount(acctName)
                println("Getting account data for account $acctName")
                it.result(dbInfo.toJSON().toString())
            }
        }.post("/:serverSecret/account/:accountName/createActor") {
            if (verifySecret(it.pathParam("serverSecret"))) {
                val acctName = it.pathParam("accountName")
                val db = database.getPersistenceDatabase()
                println("create actor request: " + it.body())
                val body = JSONObject(it.body())
                val displayName = body.getString("display_name")
                println("Creating actor for account $acctName with name $displayName")
                val acct = db.selectOrCreateAccount(acctName)
                db.createNewActorForAccount(acct, displayName)
                it.result(db.selectOrCreateAccount(acctName).toJSON().toString())
            }
        }.post("/:serverSecret/account/:accountName/deleteActor") {
            println("Delete actor request received.")
            if (verifySecret(it.pathParam("serverSecret"))) {
                val acctName = it.pathParam("accountName")
                val body = JSONObject(it.body())
                val id = body.getInt("actor_id")
                println("Deleting actor $id from account $acctName")
                val db = database.getPersistenceDatabase()
                val acct = db.selectOrCreateAccount(acctName)
                for (actor in acct.actors) {
                    if (actor.uniqueID == id) {
                        println("Actor to delete found, deletion succesful.")
                        db.deleteActor(actor)
                    }
                }
                it.result(db.selectOrCreateAccount(acctName).toJSON().toString())
            }
        }.get("/ecoData") {
            it.result(Station.createEconomyCSV())
        }.start(port)
    }

    private fun verifySecret(clientSecret: String): Boolean {
        if (!debug && serverSecret == "debug") {
            throw IllegalStateException("Server is not in debug mode yet no server secret is set!")
        } else {
            val retval = serverSecret == clientSecret
            if (!retval) {
                println("Warning: illegal client secret provided $clientSecret")
            }
            return retval
        }
    }

    private fun onClientConnect(conn: WsConnectContext) {
        println("Client connected.")
        val player = Player(conn)
        PlayerManager.addPlayer(player)
    }

    private fun onClientDisconnect(conn: WsCloseContext) {
        val player = PlayerManager.getPlayerByConnection(conn)
        if (player == null) {
            throw IllegalStateException("Connection disconnected but no player found for this connection.")
        } else {
            PlayerManager.removePlayer(player)
            if (player.initialized) {
                val playerShip: Ship = player.ship
                ShipManager.removeShip(playerShip)
            }
        }
    }

    private fun onMessageReceived(conn: WsBinaryMessageContext) {
        val player = PlayerManager.getPlayerByConnection(conn)
        if (player == null) {
            throw IllegalStateException("Connection received message but no player found for this connection.")
        } else {
            val messageStr = conn.data().toString(Charsets.UTF_8)
            val json = JSONObject(messageStr)
            player.queueIncomingMessageFromClient(json)
        }
    }

    fun composeWorldStateMessage(): JSONObject {
        val worldStateMessage = JSONObject()
        val planets = JSONArray()
        OrbiterManager.getPlanets().asSequence().map { it.createOrbiterJson() }.forEach { planets.put(it) }
        worldStateMessage.put("planets", planets)

        val stations = JSONArray()
        OrbiterManager.getStations().asSequence().map { it.createOrbiterJson() }.forEach { stations.put(it) }
        worldStateMessage.put("stations", stations)
        return worldStateMessage
    }

    private fun composeShipHeartbeatsMessageForAll(): JSONArray {
        val ships = JSONArray()
        ShipManager.getShips().map { it.createShipHeartbeatJSON() }.forEach { ships.put(it) }
        return ships
    }

    fun composeMessageForShipsAdded(inputShips: Collection<Ship>): JSONArray {
        val outputShips = JSONArray()
        inputShips.asSequence().map { it.createFullShipJSON() }.forEach { outputShips.put(it) }
        return outputShips
    }

    fun composeMessageForShipsRemoved(inputShips: Collection<Ship>): JSONArray {
        val outputShips = JSONArray()
        inputShips.asSequence().map { it.uuid }.forEach { outputShips.put(it) }
        return outputShips
    }

    fun broadcastShipDocked(ship: Ship) {
        val dockedMessage = ship.createDockedMessage()
        PlayerManager.getPlayers().forEach { it.queueShipDockedMsg(dockedMessage) }
    }

    fun broadcastShipUndocked(ship: Ship) {
        val undockedMessage = ship.createShipHeartbeatJSON()
        PlayerManager.getPlayers().forEach { it.queueShipUnDockedMsg(undockedMessage) }
    }

    private fun onSocketError(conn: WsErrorContext) {
        val player = PlayerManager.getPlayerByConnection(conn)
        if (player == null) {
            println("Connection error thrown and no player found for connection")
            throw conn.error()!!
        } else {
            println("Connection error for player id=${player.getDisplayName()}.")
            PlayerManager.removePlayer(player)
        }
    }

    private fun loadProperties(): Properties {
        val p = Properties()
        val f = File("./server.properties")
        if (f.exists()) {
            println("Found server properties file.")
            p.load(FileReader(f))
        } else {
            println("Found no server properties file, using defaults.")
        }
        return p
    }

    private fun factors(num: Int): TreeSet<Int> {
        //https://stackoverflow.com/questions/47030439/get-factors-of-numbers-in-kotlin
        val factors = TreeSet<Int>()
        if (num < 1)
            return factors
        (1..num / 2)
            .filter { num % it == 0 }
            .forEach { factors.add(it) }
        factors.add(num)
        return factors
    }

    private fun loadShipNames(): List<String> {
        return File("./ship_names.txt").readLines()
    }
}