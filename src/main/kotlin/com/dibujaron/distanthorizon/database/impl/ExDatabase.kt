package com.dibujaron.distanthorizon.database.impl

import com.dibujaron.distanthorizon.database.DhDatabase
import com.dibujaron.distanthorizon.database.persistence.PersistenceDatabase
import com.dibujaron.distanthorizon.database.script.ScriptDatabase
import com.dibujaron.distanthorizon.orbiter.CommodityType
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.name
import org.jetbrains.exposed.sql.transactions.transaction

//database impl using JetBrains Exposed
class ExDatabase(databaseUrl: String, databaseDriver: String) : DhDatabase {

    private val scriptDatabase = ExScriptDatabase()
    private val persistenceDatabase = ExPersistenceDatabase()
    init {
        val result = Database.connect(databaseUrl, driver = databaseDriver)
        println("Routes database connected. Dialect: ${result.dialect.name}, DB Version: ${result.version}, Database Name: ${result.name}. ")
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Route, RouteStep, Account, Actor, Ship)
        }
    }

    override fun getScriptDatabase(): ScriptDatabase {
        return scriptDatabase;
    }

    override fun getPersistenceDatabase(): PersistenceDatabase {
        return persistenceDatabase
    }

    object Route : IntIdTable("route") {
        val originStation = reference("origin_station_id", Station.id)
        val destinationStation = reference("dest_station_id", Station.id)
        val departureTick: Column<Int> = integer("departure_tick")
        val duration: Column<Int> = integer("duration")
        val startingLocationX: Column<Double> = double("start_loc_x")
        val startingLocationY: Column<Double> = double("start_loc_y")
        val startingRotation: Column<Double> = double("start_rotation")
        val startingVelocityX: Column<Double> = double("start_vel_x")
        val startingVelocityY: Column<Double> = double("start_vel_y")
        val shipClass: Column<String> = varchar("ship_class", 32)
        val plottedBy = reference("actor_id", Actor.id).nullable()
    }

    object RouteStep : IntIdTable("route_step") {
        val routeID = reference("route_id", Route.id)
        val stepTick: Column<Int> = integer("step_tick")
        val mainEngines: Column<Boolean> = bool("main_engines")
        val tillerLeft: Column<Boolean> = bool("tiller_left")
        val tillerRight: Column<Boolean> = bool("tiller_right")
        val portThrusters: Column<Boolean> = bool("port_thrust")
        val stbdThrusters: Column<Boolean> = bool("stbd_thrust")
        val foreThrusters: Column<Boolean> = bool("fore_thrust")
        val aftThrusters: Column<Boolean> = bool("aft_thrust")
    }

    object Account: IntIdTable("account") {
        val accountName: Column<String> = varchar("account_name", 32)
    }

    object Actor: IntIdTable("actor") {
        val ownedByAccount = reference("account_id", Account.id)
        val displayName: Column<String> = varchar("display_name", 32)
        val balance: Column<Int> = integer("balance")
        var lastDockedStation = reference("last_docked_station_id", Station.id).nullable()
        val currentShip = reference("current_ship_id", Ship.id)
    }

    object Ship: IntIdTable("ship_instance") {
        val shipClass: Column<String> = varchar("ship_class", 32)
        val shipName: Column<String> = varchar("ship_name", 64)
        val primaryColor: Column<Int> = integer("primary_color")
        val secondaryColor: Column<Int> = integer("secondary_color")
        val fuelLevel: Column<Double> = double("fuel_level")
        val holdQtyBioCells: Column<Int> = integer(CommodityType.BIO_CELLS.identifyingName).default(0)
        val holdQtyCopper: Column<Int> = integer(CommodityType.COPPER.identifyingName).default(0)
        val holdQtyThorium: Column<Int> = integer(CommodityType.THORIUM.identifyingName).default(0)
        val holdQtyEncryptedData: Column<Int> = integer(CommodityType.DATA.identifyingName).default(0)
        val holdQtySuperconductor: Column<Int> = integer(CommodityType.SUPERCONDUCTOR.identifyingName).default(0)
        val holdQtyHydrogen: Column<Int> = integer(CommodityType.HYDROGEN.identifyingName).default(0)
        val holdQtyFood: Column<Int> = integer(CommodityType.FOOD.identifyingName).default(0)
        val holdQtyIron: Column<Int> = integer(CommodityType.IRON.identifyingName).default(0)
        val holdQtyLuxuries: Column<Int> = integer(CommodityType.LUXURIES.identifyingName).default(0)
        val holdQtyMachinery: Column<Int> = integer(CommodityType.MACHINERY.identifyingName).default(0)
        val holdQtyMunitions: Column<Int> = integer(CommodityType.MUNITIONS.identifyingName).default(0)
        val holdQtyWater: Column<Int> = integer(CommodityType.WATER.identifyingName).default(0)
        val holdQtyRush: Column<Int> = integer(CommodityType.RUSH.identifyingName).default(0)
    }

    object Station: IntIdTable("station") {
        var identifyingName: Column<String> =  varchar("identifying_name", 32);
    }
}