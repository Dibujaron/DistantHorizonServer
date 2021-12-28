package com.dibujaron.distanthorizon.orbiter.station.hold.dynamic

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityStore
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.utils.NoiseUtils
import com.dibujaron.distanthorizon.utils.TimeUtils
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.roundToInt

const val UPDATE_TIME_TICKS = DHServer.TICKS_PER_SECOND * 60 * 3
class DynamicCommodityStore(type: CommodityType, val stationKey: StationKey, properties: Properties) :
    CommodityStore(type) {

    private val initialPrice: Int = properties.getProperty("${type.identifyingName}.price", "0").toIntOrNull() ?: 0
    private val initialQuantity: Int = properties.getProperty("${type.identifyingName}.initial", "0").toInt()
    private val elasticity: Int = properties.getProperty("${type.identifyingName}.elasticity", "0").toIntOrNull() ?: 0

    private var price: Double
    private var quantity: Int

    private val seed: Long
    init {
        val storeInfo = DHServer.getDatabase().getPersistenceDatabase().selectOrCreateCommodityStore(type, stationKey, initialPrice.toDouble(), initialQuantity)
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

    var lastUpdateTick = 0
    var updateCount = 0.0
    override fun tick() {
        if(DynamicEconomyManager.isMaster()) {
            val currentTick = TimeUtils.getCurrentTickAbsolute()
            if (currentTick - lastUpdateTick > UPDATE_TIME_TICKS) {
                val noise = NoiseUtils.getNoise(seed, updateCount)
                val noiseScaled = noise * elasticity
                val newPrice = initialPrice + noiseScaled
                price = newPrice
                DynamicEconomyManager.executeAsync {
                    DHServer.getDatabase().getPersistenceDatabase().updateCommodityStorePrice(type, stationKey, newPrice)
                }
                lastUpdateTick = currentTick
                updateCount++
            }
        }
    }
}