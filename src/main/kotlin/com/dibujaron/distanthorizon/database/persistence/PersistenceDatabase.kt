package com.dibujaron.distanthorizon.database.persistence

import com.dibujaron.distanthorizon.orbiter.CommodityType
import com.dibujaron.distanthorizon.orbiter.Station
import com.dibujaron.distanthorizon.ship.ColorScheme
import com.dibujaron.distanthorizon.ship.ShipClass
import java.util.*

interface PersistenceDatabase {
    fun selectOrCreateAccount(accountName: String): AccountInfo
    fun createNewActorForAccount(accountInfo: AccountInfo, actorDisplayName: String): AccountInfo?
    fun deleteActor(actorInfo: ActorInfo)
    fun updateShipOfActor(
        actor: ActorInfo,
        sc: ShipClass,
        name: String,
        colorScheme: ColorScheme,
        newFuelLevel: Double
    ): ActorInfo?

    fun updateActorBalance(actor: ActorInfo, newBal: Int): ActorInfo?
    fun updateActorDockedStation(actor: ActorInfo, station: StationKey): ActorInfo?
    fun updateShipHold(ship: ShipInfo, commodity: CommodityType, amount: Int)
    fun updateShipFuelLevel(ship: ShipInfo, newFuelLevel: Double)
    fun getWealthiestActors(limit: Int): List<ActorInfo>
    fun selectOrCreateStation(stationName: String, properties: Properties): StationKey
}