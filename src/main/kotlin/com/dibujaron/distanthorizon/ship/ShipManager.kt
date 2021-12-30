package com.dibujaron.distanthorizon.ship

import com.dibujaron.distanthorizon.DHModule
import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.player.PlayerManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ShipManager : DHModule {
    private val shipMap = ConcurrentHashMap<UUID, Ship>()

    fun getShips(): Collection<Ship> {
        return shipMap.values
    }

    override fun tick() {
        getShips().forEach { it.tick() }
    }

    fun addShip(ship: Ship) {
        shipMap[ship.uuid] = ship
        val message = DHServer.composeMessageForShipsAdded(Collections.singletonList(ship))
        PlayerManager.getPlayers().forEach { it.queueShipsAddedMsg(message) }
    }

    fun removeShip(ship: Ship) {
        val message = DHServer.composeMessageForShipsRemoved(Collections.singletonList(ship))
        PlayerManager.getPlayers().forEach { it.queueShipsRemovedMsg(message) }
        shipMap.remove(ship.uuid)
    }
}