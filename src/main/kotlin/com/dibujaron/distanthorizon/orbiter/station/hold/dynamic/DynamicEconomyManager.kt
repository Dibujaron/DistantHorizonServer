package com.dibujaron.distanthorizon.orbiter.station.hold.dynamic

import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.database.persistence.StationKey
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import java.util.*
import java.util.concurrent.Executors
import kotlin.concurrent.thread

const val UPDATE_INTERVAL_MS = 5000L
object DynamicEconomyManager {

    private var IS_MASTER = true
    private var USE_DYNAMIC_ECONOMY = false

    private val storeMap = HashMap<StoreKey, DynamicCommodityStore>()
    private val asyncExecutor = Executors.newFixedThreadPool(1)
    fun moduleInit(serverProperties: Properties) {
        USE_DYNAMIC_ECONOMY = serverProperties.getProperty("economy.dynamic", "true").toBoolean()
        if (USE_DYNAMIC_ECONOMY) {
            IS_MASTER = serverProperties.getProperty("master", "true").toBoolean()
            thread{ runThread()}
        }
    }

    fun isEnabled(): Boolean
    {
        return USE_DYNAMIC_ECONOMY
    }

    fun isMaster(): Boolean
    {
        return IS_MASTER
    }

    fun executeAsync(command: () -> Unit){
        asyncExecutor.execute(command)
    }

    private fun runThread() {
        while(true){
            Thread.sleep(UPDATE_INTERVAL_MS)
            update()
        }
    }

    private fun update() {
        val persistenceDatabase = DHServer.getDatabase().getPersistenceDatabase()
        persistenceDatabase.selectCommodityStoreStatus()
            .forEach{
                val key = StoreKey(it.commodityType, it.stationKey)
                val store = storeMap[key]
                store?.updateFromDatabase(it.price, it.quantity)
            }
    }

    fun registerCommodityStore(type: CommodityType, stationKey: StationKey, store: DynamicCommodityStore)
    {
        storeMap[StoreKey(type, stationKey)] = store
    }
}

class StoreKey(val type: CommodityType, val key: StationKey){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StoreKey

        if (type != other.type) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + key.hashCode()
        return result
    }
}