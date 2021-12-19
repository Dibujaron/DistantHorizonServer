package com.dibujaron.distanthorizon.ship

import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.docking.ShipClassDockingPort
import com.dibujaron.distanthorizon.orbiter.CommodityType
import com.dibujaron.distanthorizon.player.Player
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.HashMap
import kotlin.random.Random

class ShipClass(
    properties: Properties

) {
    //RIJAY_MOCKINGBIRD(ShipMake.RIJAY, "Mockingbird", 6.0, 120.0, 30.0),
    //PHE_THUMPER(ShipMake.PHE, "Thumper", 3.0, 120.0, 30.0);

    //	"phe.thumper"
    //	"rijay.mockingbird"
    val identifyingName: String = properties.getProperty("identifyingName").trim()
    val displayName: String = properties.getProperty("displayName").trim()
    val manufacturer: Manufacturer = Manufacturer.fromString(properties.getProperty("manufacturer").trim())
    val qualifiedName = "${manufacturer.identifyingName}.$identifyingName"
    val rotationPower: Double = properties.getProperty("rotationPower").toDouble()
    val mainThrust: Double = properties.getProperty("mainThrust").toDouble()
    val manuThrust: Double = properties.getProperty("manuThrust").toDouble()
    val dockingPortCount: Int = properties.getProperty("dockingPortCount").toInt()
    val holdSize: Int = properties.getProperty("holdSize").toInt()
    val price: Int = properties.getProperty("price").toInt()
    val fuelTankSize: Int = properties.getProperty("fuelTankSize").toInt()
    val fuelBurnRatePerSecond: Double = properties.getProperty("fuelBurnRate").toDouble()
    val fuelBurnRatePerTick = fuelBurnRatePerSecond / 20
    val dockingPorts = generateSequence(0) { it + 1 }
        .take(dockingPortCount)
        .map {
            Pair(
                Vector2(
                    properties.getProperty("dockingPort.$it.relativePosX").toDouble(),
                    properties.getProperty("dockingPort.$it.relativePosY").toDouble()
                ), properties.getProperty("dockingPort.$it.rotationDegrees").toDouble()
            )
        }
        .map { ShipClassDockingPort(it.first, it.second) }.toList()

    val primaryCount: Int = properties.getProperty("primaryCount").toInt()
    val primaryColors = generateSequence(0) { it + 1 }
        .take(primaryCount)
        .map {
            ShipColor.fromHexString(properties.getProperty("color.primary.$it").toString())
        }.toList()

    val secondaryCount: Int = properties.getProperty("secondaryCount").toInt()
    val secondaryColors = generateSequence(0) { it + 1 }
        .take(secondaryCount)
        .map {
            ShipColor.fromHexString(properties.getProperty("color.secondary.$it").toString())
        }.toList()    //todo use this in the Ship toJson() - currently duplicated

    fun toJSON(player: Player, random: Random, percentage: Int): JSONObject {
        val retval = JSONObject()
        retval.put("qualified_name", qualifiedName)
        retval.put("identifying_name", identifyingName)
        retval.put("display_name", displayName)
        retval.put("hold_size", holdSize)
        retval.put("main_engine_thrust", mainThrust)
        retval.put("manu_engine_thrust", manuThrust)
        retval.put("rotation_power", rotationPower)
        retval.put("price", getPurchaseCost(player.ship.type))
        retval.put("fuel_tank_size", fuelTankSize)
        val colorsJson = JSONArray()
        for (primaryColor in primaryColors) {
            for (secondaryColor in secondaryColors) {
                if (random.nextFloat() * 100 > percentage) {
                    val colorInfo = JSONArray()
                    colorInfo.put(primaryColor.toJSON())
                    colorInfo.put(secondaryColor.toJSON())
                    colorsJson.put(colorInfo)
                }
            }
        }
        retval.put("colors", colorsJson)
        return retval
    }

    fun getPurchaseCost(existingShipClass: ShipClass): Int
    {
        var cost = price
        if (manufacturer == existingShipClass.manufacturer){
            cost -= (existingShipClass.price / 2) //same manufacturer trade-ins get 50% value
        } else {
            cost -= (existingShipClass.price / 4)
        }
        return cost
    }

    fun generateRandomHoldMap(): HashMap<CommodityType, Int> {
        val holdSize = this.holdSize
        var fillAmount = 0
        val retval = HashMap<CommodityType, Int>()
        CommodityType.values().forEach {
            val remainingSpace = holdSize - fillAmount
            val randAmt: Int = (Random.nextFloat() * remainingSpace).toInt()
            retval[it] = randAmt
            fillAmount += randAmt
        }
        return retval
    }

    fun getGoodColorScheme(): ColorScheme
    {
        //return Pair(this.primaryColors[0],this.secondaryColors[0])
        var primaryColor = this.primaryColors.random()
        var secondaryColor = this.secondaryColors.random()
        var tries = 0
        while(primaryColor.colorDistance(secondaryColor) < 10 && tries < 10000){
            primaryColor = this.primaryColors.random()
            secondaryColor = this.secondaryColors.random()
            tries++
        }
        return ColorScheme(primaryColor, secondaryColor)
    }

}