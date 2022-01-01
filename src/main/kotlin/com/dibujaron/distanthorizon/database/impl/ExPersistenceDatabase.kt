package com.dibujaron.distanthorizon.database.impl

import StationKeyInternal
import com.dibujaron.distanthorizon.DHServer
import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.database.persistence.*
import com.dibujaron.distanthorizon.orbiter.station.hold.CommodityType
import com.dibujaron.distanthorizon.ship.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.collections.HashMap

class ExPersistenceDatabase : PersistenceDatabase {

    inner class AccountInfoInternal(
        val id: EntityID<Int>,
        accountName: String,
        actors: List<ActorInfoInternal>
    ) : AccountInfo(accountName, actors)

    inner class ActorInfoInternal(
        val id: EntityID<Int>,
        displayName: String,
        balance: Int,
        lastDockedStation: StationKey?,
        ship: ShipInfoInternal
    ) : ActorInfo(id.value, displayName, balance, lastDockedStation, ship)

    inner class ShipInfoInternal(
        val id: EntityID<Int>,
        shipClass: ShipClass,
        shipName: String,
        primaryColor: ShipColor,
        secondaryColor: ShipColor,
        holdMap: Map<CommodityType, Int>,
        fuelLevel: Double
    ) : ShipInfo(shipClass, shipName, primaryColor, secondaryColor, holdMap, fuelLevel)

    override fun selectOrCreateStation(stationName: String, properties: Properties): StationKey {
        val nameFilter = (ExDatabase.Station.identifyingName eq stationName)
        val stationRow = transaction {
            val firstResult: ResultRow? = ExDatabase.Station
                .select { nameFilter }
                .firstOrNull()

            if (firstResult == null) {
                val id = ExDatabase.Station.insertAndGetId {
                    it[ExDatabase.Station.identifyingName] = stationName
                }
                val idFilter = ExDatabase.Station.id eq id
                ExDatabase.Station.select { idFilter }.first()
            } else {
                firstResult
            }
        }

        return StationKeyInternal(stationRow[ExDatabase.Station.id])
    }

    override fun selectCommodityStoreStatus(): List<CommodityStoreInfo> {
        return transaction {
            ExDatabase.StationCommmodityStore
                .selectAll()
                .map { mapCommodityStoreInfo(it) }
        }
    }

    private fun mapCommodityStoreInfo(row: ResultRow): CommodityStoreInfo {
        return CommodityStoreInfo(
            StationKeyInternal(row[ExDatabase.StationCommmodityStore.station]),
            CommodityType.fromString(row[ExDatabase.StationCommmodityStore.commodity]),
            row[ExDatabase.StationCommmodityStore.price],
            row[ExDatabase.StationCommmodityStore.quantity]
        )
    }

    override fun updateCommodityStoreQuantity(commodity: CommodityType, station: StationKey, newQuantity: Int) {
        val stationKeyFilter = ExDatabase.StationCommmodityStore.station eq (station as StationKeyInternal).id
        val commodityTypeFilter = ExDatabase.StationCommmodityStore.commodity eq commodity.identifyingName
        transaction {
            ExDatabase.StationCommmodityStore.update({ stationKeyFilter and commodityTypeFilter }) {
                it[quantity] = newQuantity
            }
        }
    }

    override fun updateCommodityStorePrice(commodity: CommodityType, station: StationKey, newPrice: Double) {
        val stationKeyFilter = ExDatabase.StationCommmodityStore.station eq (station as StationKeyInternal).id
        val commodityTypeFilter = ExDatabase.StationCommmodityStore.commodity eq commodity.identifyingName
        transaction {
            ExDatabase.StationCommmodityStore.update({ stationKeyFilter and commodityTypeFilter }) {
                it[price] = newPrice
            }
        }
    }

    override fun selectOrCreateCommodityStore(
        commodity: CommodityType,
        station: StationKey,
        initialPrice: Double,
        initialQuantity: Int
    ): CommodityStoreInfo {
        val stationKeyFilter = ExDatabase.StationCommmodityStore.station eq (station as StationKeyInternal).id
        val commodityTypeFilter = ExDatabase.StationCommmodityStore.commodity eq commodity.identifyingName
        val storeInfo = transaction {
            val firstResult: ResultRow? = ExDatabase.StationCommmodityStore
                .select { stationKeyFilter and commodityTypeFilter }
                .firstOrNull()

            if (firstResult == null) {
                val id = ExDatabase.StationCommmodityStore.insertAndGetId {
                    it[ExDatabase.StationCommmodityStore.station] = station.id
                    it[ExDatabase.StationCommmodityStore.commodity] = commodity.identifyingName
                    it[price] = initialPrice
                    it[quantity] = initialQuantity
                }
                val idFilter = (ExDatabase.StationCommmodityStore.id eq id)
                ExDatabase.StationCommmodityStore.select { idFilter }.first()
            } else {
                firstResult
            }
        }
        return CommodityStoreInfo(
            station,
            commodity,
            storeInfo[ExDatabase.StationCommmodityStore.price],
            storeInfo[ExDatabase.StationCommmodityStore.quantity]
        )
    }

    override fun selectOrCreateAccount(accountName: String): AccountInfo {
        val nameFilter = (ExDatabase.Account.accountName eq accountName)
        val accountRow = transaction {
            val firstResult: ResultRow? = ExDatabase.Account
                .select { nameFilter }
                .firstOrNull()

            if (firstResult == null) {
                val id = ExDatabase.Account.insertAndGetId {
                    it[ExDatabase.Account.accountName] = accountName
                }
                val idFilter = (ExDatabase.Account.id eq id)
                ExDatabase.Account
                    .select { idFilter }
                    .first()
            } else {
                firstResult
            }
        }
        val accountIdFilter = (ExDatabase.Actor.ownedByAccount eq accountRow[ExDatabase.Account.id])
        val actors = transaction {
            ExDatabase.Actor.join(
                ExDatabase.Ship,
                JoinType.INNER,
                additionalConstraint = { ExDatabase.Actor.currentShip eq ExDatabase.Ship.id })
                .select(accountIdFilter)
                .map { mapActorInfo(it) }
                .toList()
        }
        return AccountInfoInternal(
            accountRow[ExDatabase.Account.id],
            accountRow[ExDatabase.Account.accountName],
            actors
        )
    }

    private fun mapActorInfo(row: ResultRow): ActorInfoInternal {
        return ActorInfoInternal(
            row[ExDatabase.Actor.id],
            row[ExDatabase.Actor.displayName],
            row[ExDatabase.Actor.balance],
            row[ExDatabase.Actor.lastDockedStation]?.let { StationKeyInternal(it) },
            mapShipInfo(row)
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapShipInfo(row: ResultRow): ShipInfoInternal {
        val holdMap = HashMap<CommodityType, Int>()
        CommodityType.values().forEach { ct ->
            val commodityName = ct.identifyingName
            val col: Column<Int> = ExDatabase.Ship.columns.find { col -> col.name == commodityName } as Column<Int>
            holdMap[ct] = row[col]
        }
        return ShipInfoInternal(
            row[ExDatabase.Ship.id],
            ShipClassManager.getShipClassRequired(row[ExDatabase.Ship.shipClass]),
            row[ExDatabase.Ship.shipName],
            ShipColor.fromInt(row[ExDatabase.Ship.primaryColor]),
            ShipColor.fromInt(row[ExDatabase.Ship.secondaryColor]),
            holdMap,
            row[ExDatabase.Ship.fuelLevel]
        )
    }

    override fun createNewActorForAccount(accountInfo: AccountInfo, actorDisplayName: String): AccountInfo {
        if (accountInfo is AccountInfoInternal) {
            val acctId = accountInfo.id
            val startingShipClass = ShipClassManager.getShipClass(DHServer.playerStartingShip)!!
            val colors = startingShipClass.getGoodColorScheme()
            val shipId = transaction {
                ExDatabase.Ship.insertAndGetId {
                    it[shipClass] = DHServer.playerStartingShip
                    it[shipName] = DHServer.shipNames.random()
                    it[primaryColor] = colors.primaryColor.toInt()//ShipColor(Color(0,148,255)),
                    it[secondaryColor] = colors.secondaryColor.toInt()
                    it[fuelLevel] = startingShipClass.fuelTankSize.toDouble()
                }
            }
            transaction {
                ExDatabase.Actor.insert {
                    it[ownedByAccount] = acctId
                    it[displayName] = actorDisplayName
                    it[balance] = DHServer.startingBalance
                    it[lastDockedStation] = null
                    it[currentShip] = shipId
                }
            }
            return selectOrCreateAccount(accountInfo.accountName)
        } else {
            throw IllegalStateException("Object must be from same database")
        }
    }

    override fun deleteActor(actorInfo: ActorInfo) {
        if (actorInfo is ActorInfoInternal) {
            val ship = actorInfo.ship
            if (ship is ShipInfoInternal) {
                val actorIdFilter = (ExDatabase.Actor.id eq actorInfo.id)
                val routeActorIdFilter = (ExDatabase.Route.plottedBy eq actorInfo.id)
                transaction {
                    ExDatabase.Route.update({ routeActorIdFilter }) {
                        it[plottedBy] = null
                    }
                    ExDatabase.Actor.deleteWhere { actorIdFilter }
                }
            }
        } else {
            throw IllegalStateException("Object must be from same database")
        }
    }

    override fun updateShipOfActor(
        actor: ActorInfo,
        sc: ShipClass,
        name: String,
        colorScheme: ColorScheme,
        newFuelLevel: Double
    ): ActorInfo {
        if (actor is ActorInfoInternal) {
            val oldShip = actor.ship
            if (oldShip is ShipInfoInternal) {
                val shipIdFilter = (ExDatabase.Ship.id eq oldShip.id)
                transaction {
                    ExDatabase.Ship.update({ shipIdFilter }) {
                        it[shipClass] = sc.qualifiedName
                        it[shipName] = name
                        it[primaryColor] = colorScheme.primaryColor.toInt()
                        it[secondaryColor] = colorScheme.secondaryColor.toInt()
                        it[fuelLevel] = newFuelLevel
                    }
                    //todo this needs to update the hold, can exceed hold capacity
                }
                return transaction {
                    ExDatabase.Actor.join(
                        ExDatabase.Ship,
                        JoinType.INNER,
                        additionalConstraint = { ExDatabase.Actor.currentShip eq ExDatabase.Ship.id })
                        .select { ExDatabase.Actor.id eq actor.id }
                        .map { mapActorInfo(it) }
                        .first()
                }
            }
        }
        throw java.lang.IllegalStateException("Object must be from same db")
    }

    override fun updateActorBalance(actor: ActorInfo, newBal: Int): ActorInfo? {
        if (actor is ActorInfoInternal) {
            val actorIdFilter = (ExDatabase.Actor.id eq actor.id)
            transaction {
                ExDatabase.Actor.update({ actorIdFilter }) {
                    it[balance] = newBal
                }
            }
            return transaction {
                ExDatabase.Actor.join(
                    ExDatabase.Ship,
                    JoinType.INNER,
                    additionalConstraint = { ExDatabase.Actor.currentShip eq ExDatabase.Ship.id })
                    .select { ExDatabase.Actor.id eq actor.id }
                    .map { mapActorInfo(it) }
                    .first()
            }
        }
        throw java.lang.IllegalStateException("Object must be from same db")
    }

    override fun updateActorDockedStation(actor: ActorInfo, station: StationKey): ActorInfo? {
        if (actor is ActorInfoInternal) {
            val actorIdFilter = (ExDatabase.Actor.id eq actor.id)
            transaction {
                ExDatabase.Actor.update({ actorIdFilter }) {
                    it[lastDockedStation] = (station as StationKeyInternal).id
                }
            }
            return transaction {
                ExDatabase.Actor.join(
                    ExDatabase.Ship,
                    JoinType.INNER,
                    additionalConstraint = { ExDatabase.Actor.currentShip eq ExDatabase.Ship.id })
                    .select { ExDatabase.Actor.id eq actor.id }
                    .map { mapActorInfo(it) }
                    .first()
            }
        }
        throw java.lang.IllegalStateException("Object must be from same db")
    }

    override fun updateShipFuelLevel(ship: ShipInfo, newFuelLevel: Double) {
        if (ship is ShipInfoInternal) {
            val shipIdFilter = (ExDatabase.Ship.id eq ship.id)
            transaction {
                ExDatabase.Ship.update({ shipIdFilter }) {
                    it[fuelLevel] = newFuelLevel
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun updateShipHold(ship: ShipInfo, commodity: CommodityType, amount: Int) {
        if (ship is ShipInfoInternal) {
            val shipIDFilter = (ExDatabase.Ship.id eq ship.id)
            val commodityCol: Column<Int> =
                ExDatabase.Ship.columns.find { col -> col.name == commodity.identifyingName } as Column<Int>
            transaction {
                ExDatabase.Ship.update({ shipIDFilter }) {
                    it[commodityCol] = amount
                }
            }
        } else {
            throw java.lang.IllegalStateException("Object must be from same db")
        }
    }

    override fun getWealthiestActors(limit: Int): List<ActorInfo> {
        return transaction {
            ExDatabase.Actor.join(
                ExDatabase.Ship,
                JoinType.INNER,
                additionalConstraint = { ExDatabase.Actor.currentShip eq ExDatabase.Ship.id })
                .selectAll()
                .orderBy(ExDatabase.Actor.balance to SortOrder.DESC)
                .limit(limit)
                .map { mapActorInfo(it) }
        }
    }

    override fun selectWaitingPassengersAtStation(station: StationKey): List<WaitingPassengerGroupInfo> {
        val stationIDFilter = (ExDatabase.StationPassengerGroup.station eq (station as StationKeyInternal).id)
        return transaction {
            ExDatabase.StationPassengerGroup.select { stationIDFilter }
                .map { mapWaitingPassengerGroupInfo(it) }
        }
    }

    override fun selectWaitingPassengers(): List<WaitingPassengerGroupInfo> {
        return transaction {
            ExDatabase.StationPassengerGroup.selectAll()
                .map { mapWaitingPassengerGroupInfo(it) }
        }
    }

    private fun mapWaitingPassengerGroupInfo(it: ResultRow): WaitingPassengerGroupInfoInternal {
        val sourceStationKey = StationKeyInternal(it[ExDatabase.StationPassengerGroup.station])
        val destStationKey = StationKeyInternal(it[ExDatabase.StationPassengerGroup.destinationStation])
        return WaitingPassengerGroupInfoInternal(
            it[ExDatabase.StationPassengerGroup.id],
            sourceStationKey,
            destStationKey,
            it[ExDatabase.StationPassengerGroup.quantity],
            it[ExDatabase.StationPassengerGroup.waitingSince]
        )
    }

    override fun addWaitingPassengers(
        station: StationKey,
        destStation: StationKey,
        numPassengers: Int,
        waitingSince: Long
    ) {
        transaction {
            ExDatabase.StationPassengerGroup.insert {
                it[ExDatabase.StationPassengerGroup.station] = (station as StationKeyInternal).id
                it[destinationStation] = (destStation as StationKeyInternal).id
                it[quantity] = numPassengers
                it[ExDatabase.StationPassengerGroup.waitingSince] = waitingSince
            }
        }
    }

    override fun movePassengersFromStationToShip(
        ship: ShipInfo,
        originStation: StationKey,
        destStation: StationKey,
        embarkLocation: Vector2,
        embarkTime: Long,
        quantity: Int
    ) {
        val sourceStationFilter = (ExDatabase.StationPassengerGroup.station eq (originStation as StationKeyInternal).id)
        val targetStationFilter =
            (ExDatabase.StationPassengerGroup.destinationStation eq (destStation as StationKeyInternal).id)
        transaction {
            val groups = ExDatabase.Account
                .select { sourceStationFilter and targetStationFilter }
                .orderBy(ExDatabase.StationPassengerGroup.waitingSince, SortOrder.ASC)
                .map { mapWaitingPassengerGroupInfo(it) }

            var quantityRemaining = quantity
            for (waitingGroup in groups) {
                if (waitingGroup.quantity >= quantityRemaining) {
                    ExDatabase.StationPassengerGroup.deleteWhere { ExDatabase.StationPassengerGroup.id eq waitingGroup.id }
                    quantityRemaining -= waitingGroup.quantity
                } else {
                    val newQuantity = waitingGroup.quantity - quantityRemaining
                    ExDatabase.StationCommmodityStore.update({ ExDatabase.StationPassengerGroup.id eq waitingGroup.id }) {
                        it[ExDatabase.StationPassengerGroup.quantity] = newQuantity
                    }
                    quantityRemaining = 0
                }
                if (quantityRemaining <= 0) {
                    break;
                }
            }
            ExDatabase.EmbarkedPassengerGroup.insert {
                it[ExDatabase.EmbarkedPassengerGroup.ship] = (ship as ShipInfoInternal).id
                it[ExDatabase.EmbarkedPassengerGroup.originStation] = (originStation as StationKeyInternal).id
                it[destinationStation] = (destStation as StationKeyInternal).id
                it[loadedLocationX] = embarkLocation.x
                it[loadedLocationY] = embarkLocation.y
                it[loadedTime] = embarkTime
                it[ExDatabase.EmbarkedPassengerGroup.quantity] = quantity
            }
        }
    }

    override fun getPassengersOnShip(ship: ShipInfo): List<EmbarkedPassengerGroup> {
        val shipFilter = (ExDatabase.EmbarkedPassengerGroup.ship eq (ship as ShipInfoInternal).id)
        return transaction {
            ExDatabase.EmbarkedPassengerGroup.select { shipFilter }
                .map {
                    val originStation = StationKeyInternal(it[ExDatabase.EmbarkedPassengerGroup.originStation])
                    val destStation = StationKeyInternal(it[ExDatabase.EmbarkedPassengerGroup.destinationStation])
                    val originLocation = Vector2(
                        it[ExDatabase.EmbarkedPassengerGroup.loadedLocationX],
                        it[ExDatabase.EmbarkedPassengerGroup.loadedLocationY]
                    )
                    EmbarkedPassengerGroup(
                        originStation,
                        originLocation,
                        it[ExDatabase.EmbarkedPassengerGroup.loadedTime],
                        destStation,
                        it[ExDatabase.EmbarkedPassengerGroup.quantity]
                    )
                }
        }
    }

    override fun clearPassengersToStation(ship: ShipInfo, arrivalStation: StationKey) {
        val shipFilter = (ExDatabase.EmbarkedPassengerGroup.ship eq (ship as ShipInfoInternal).id)
        val destStationFilter =
            (ExDatabase.EmbarkedPassengerGroup.destinationStation eq (arrivalStation as StationKeyInternal).id)
        transaction {
            ExDatabase.EmbarkedPassengerGroup.deleteWhere { shipFilter and destStationFilter }
        }
    }

    override fun clearWaitingPassengersWaitingSinceBefore(timestampThreshold: Long) {
        val timestampFilter = (ExDatabase.StationPassengerGroup.waitingSince lessEq timestampThreshold)
        transaction {
            ExDatabase.StationPassengerGroup.deleteWhere { timestampFilter }
        }
    }
}