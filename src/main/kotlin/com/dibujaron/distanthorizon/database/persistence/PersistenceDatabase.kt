package com.dibujaron.distanthorizon.database.persistence

import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.ship.ColorScheme
import com.dibujaron.distanthorizon.ship.EmbarkedPassengerGroup
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

    fun updateActorBalance(actor: ActorInfo, newBal: Int)
    fun updateActorDockedStation(actor: ActorInfo, station: StationKey)
    fun updateShipHold(ship: ShipInfo, commodity: CommodityType, amount: Int)
    fun updateShipFuelLevel(ship: ShipInfo, newFuelLevel: Double)
    fun getWealthiestActors(limit: Int): List<ActorInfo>
    fun selectOrCreateStation(stationName: String, properties: Properties): StationKey
    fun selectCommodityStoreStatus(): List<CommodityStoreInfo>
    fun updateCommodityStoreQuantity(commodity: CommodityType, station: StationKey, newQuantity: Int)
    fun updateCommodityStorePrice(commodity: CommodityType, station: StationKey, newPrice: Double)
    fun selectOrCreateCommodityStore(
        commodity: CommodityType,
        station: StationKey,
        initialPrice: Double,
        initialQuantity: Int
    ): CommodityStoreInfo

    fun selectWaitingPassengersAtStation(station: StationKey): List<WaitingPassengerGroupInfo>
    fun selectWaitingPassengers(): List<WaitingPassengerGroupInfo>
    fun addWaitingPassengers(station: StationKey, destStation: StationKey, numPassengers: Int, waitingSince: Long)
    fun movePassengersFromStationToShip(
        ship: ShipInfo,
        originStation: StationKey,
        destStation: StationKey,
        embarkLocation: Vector2,
        embarkTime: Long,
        quantity: Int
    )

    fun getPassengersOnShip(ship: ShipInfo): List<EmbarkedPassengerGroup>
    fun clearPassengersToStation(ship: ShipInfo, arrivalStation: StationKey)
    fun clearWaitingPassengersWaitingSinceBefore(timestampThreshold: Long)
}