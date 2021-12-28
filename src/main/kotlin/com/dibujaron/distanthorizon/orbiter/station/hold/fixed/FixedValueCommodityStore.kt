package com.dibujaron.distanthorizon.orbiter.station.hold.fixed

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityStore
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.utils.TimeUtils
import java.util.*

const val UPDATE_TIME_TICKS = DHServer.TICKS_PER_SECOND * 5

class FixedValueCommodityStore(type: CommodityType, properties: Properties) : CommodityStore(type) {

    private val initialPrice: Int = properties.getProperty("${type.identifyingName}.price", "0").toIntOrNull() ?: 0
    private val initialQuantity: Int = properties.getProperty("${type.identifyingName}.initial", "0").toInt()
    private var quantityAvailableInternal = initialQuantity
    private var priceInternal = initialPrice

    private val productionConsumptionRate =
        if (initialQuantity > 0) (priceInternal * 10) else -1 * (priceInternal * 10)

    override fun getPrice(): Int {
        return priceInternal
    }

    override fun getQuantityAvailable(): Int {
        return quantityAvailableInternal
    }

    override fun updateQuantityAvailable(delta: Int) {
        quantityAvailableInternal += delta
    }

    var lastUpdateTick = 0
    override fun tick() {
        val currentTick = TimeUtils.getCurrentTickAbsolute()
        if (currentTick - lastUpdateTick > UPDATE_TIME_TICKS) {
            var newQty = quantityAvailableInternal + productionConsumptionRate
            if (newQty > initialQuantity) {
                newQty = initialQuantity
            } else if (newQty < 0) {
                newQty = 0
            }
            quantityAvailableInternal = newQty
            lastUpdateTick = currentTick
        }
    }
}