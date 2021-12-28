package com.dibujaron.distanthorizon.orbiter.station.hold.dynamic

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityStore
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.orbiter.station.hold.fixed.UPDATE_TIME_TICKS
import com.dibujaron.distanthorizon.utils.NoiseUtils
import com.dibujaron.distanthorizon.utils.TimeUtils
import java.util.*
import kotlin.math.roundToInt

const val PRICE_UPDATE_TIME_TICKS = DHServer.TICKS_PER_SECOND * 60 * 3
const val QUANTITY_UPDATE_TIME_TICKS = DHServer.TICKS_PER_SECOND * 5

class DynamicCommodityStore(type: CommodityType, val stationKey: StationKey, properties: Properties) :
    CommodityStore(type) {

    private val initialPrice: Int = properties.getProperty("${type.identifyingName}.price", "0").toIntOrNull() ?: 0
    private val initialQuantity: Int = properties.getProperty("${type.identifyingName}.initial", "0").toInt()
    private val elasticity: Int = properties.getProperty("${type.identifyingName}.elasticity", "0").toIntOrNull() ?: 0

    private var price: Double
    private var quantity: Int

    private val productionConsumptionRate =
        if (initialQuantity > 0) (initialPrice * 10) else -1 * (initialPrice * 10)

    private val seed: Long

    init {
        val storeInfo = DHServer.getDatabase().getPersistenceDatabase()
            .selectOrCreateCommodityStore(type, stationKey, initialPrice.toDouble(), initialQuantity)
        price = storeInfo.price
        quantity = storeInfo.quantity
        DynamicEconomyManager.registerCommodityStore(type, stationKey, this)
        seed = (System.currentTimeMillis() * type.hashCode() * stationKey.hashCode())
    }

    fun updateFromDatabase(newPrice: Double, newQuantity: Int) {
        price = newPrice
        quantity = newQuantity
    }

    override fun getQuantityAvailable(): Int {
        return quantity
    }

    override fun updateQuantityAvailable(delta: Int) {
        val newQuantity = quantity + delta
        quantity = newQuantity
        DynamicEconomyManager.executeAsync {
            DHServer.getDatabase().getPersistenceDatabase().updateCommodityStoreQuantity(type, stationKey, newQuantity)
        }
    }

    override fun getPrice(): Int {
        return price.roundToInt()
    }

    override fun tick() {
        if (DynamicEconomyManager.isMaster()) {
            priceTick()
            quantityTick()
        }
    }

    var lastUpdateTickQuantity = 0
    private fun quantityTick() {
        val currentTick = TimeUtils.getCurrentTickAbsolute()
        if (currentTick - lastUpdateTickQuantity > QUANTITY_UPDATE_TIME_TICKS) {
            var newQty = quantity + productionConsumptionRate
            if (newQty > initialQuantity) {
                newQty = initialQuantity
            } else if (newQty < 0) {
                newQty = 0
            }
            if (newQty != quantity) {
                DynamicEconomyManager.executeAsync {
                    DHServer.getDatabase().getPersistenceDatabase()
                        .updateCommodityStoreQuantity(type, stationKey, newQty)
                }
            }
            quantity = newQty
            lastUpdateTickQuantity = currentTick
        }
    }

    var lastUpdateTickPrice = 0
    var updateCountPrice = 0.0
    private fun priceTick() {
        val currentTick = TimeUtils.getCurrentTickAbsolute()
        if (currentTick - lastUpdateTickPrice > PRICE_UPDATE_TIME_TICKS) {
            val noise = NoiseUtils.getNoise(seed, updateCountPrice)
            val noiseScaled = noise * elasticity
            val newPrice = initialPrice + noiseScaled
            price = newPrice
            DynamicEconomyManager.executeAsync {
                DHServer.getDatabase().getPersistenceDatabase().updateCommodityStorePrice(type, stationKey, newPrice)
            }
            lastUpdateTickPrice = currentTick
            updateCountPrice++
        }
    }
}