package com.dibujaron.distanthorizon.orbiter

import com.dibujaron.distanthorizon.Vector2
import com.dibujaron.distanthorizon.database.persistence.StationKey
import java.io.File
import java.io.FileReader
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.pow

object OrbiterManager {
    private const val MIN_GRAVITY_FORCE_CUTOFF = 0.2

    private val orbitersMap: HashMap<String, Orbiter> = HashMap()
    private val planetsMap: HashMap<String, Planet> = HashMap()
    private val stationsMap: HashMap<String, Station> = HashMap()
    private val stationKeyMap: HashMap<StationKey, Station> = HashMap();

    private const val GRAVITY_FUDGE = 50.0
    val GRAVITY_CONSTANT = 6.67408 * 10.0.pow(-11.0) * GRAVITY_FUDGE

    fun tick() {
        getOrbiters().forEach { it.tick() }
    }

    fun getPlanet(name: String): Planet? {
        return planetsMap[name];
    }

    fun getPlanets(): Collection<Planet> {
        return planetsMap.values
    }

    fun getOrbiter(name: String): Orbiter? {
        return orbitersMap[name];
    }

    fun getOrbiters(): Collection<Orbiter> {
        return orbitersMap.values
    }

    fun getStationByName(name: String): Station? {
        return stationsMap[name];
    }

    fun getStationByNameRequired(name: String): Station {
        val s = getStationByName(name);
        if (s == null) {
            throw IllegalStateException("No station found with name $name")
        } else {
            return s
        }
    }

    fun getStationByKey(key: StationKey): Station?
    {
        return stationKeyMap[key];
    }

    fun getStationByKeyRequired(key: StationKey): Station
    {
        val s = getStationByKey(key);
        if (s == null) {
            throw IllegalStateException("No station found with key $key")
        } else {
            return s
        }
    }

    fun getStations(): Collection<Station> {
        return stationsMap.values
    }

    fun calculateGravityAtTick(tickOffset: Double, globalPosAtTick: Vector2): Vector2 {
        var accel = Vector2(0, 0)
        getPlanets().asSequence()
            .forEach {
                val planetPosAtTick = it.globalPosAtTick(tickOffset)
                val offset = (planetPosAtTick - globalPosAtTick)
                var rSquared = offset.lengthSquared
                if (rSquared < it.minRadiusSquared) {
                    rSquared = it.minRadiusSquared.toDouble()
                }
                val forceMag = GRAVITY_CONSTANT * it.mass / rSquared
                if (forceMag > MIN_GRAVITY_FORCE_CUTOFF) {
                    accel += (offset.normalized() * forceMag)
                }
            }
        return accel
    }

    init {
        recursiveInitOrbiters(null, File("./world"))
        orbitersMap.values.forEach { it.initialize() }
        stationsMap.values.forEach { it.initAiScripts() }
    }

    private fun recursiveInitOrbiters(parentName: String?, folder: File) {
        folder.walk()
            .maxDepth(1)
            .filter { it.name.endsWith(".properties") }
            .forEach {
                val reader = FileReader(it)
                val props = Properties()
                props.load(reader)
                val orbiterName = it.nameWithoutExtension
                if (orbiterName.startsWith("Stn_")) {
                    val stn = Station(parentName, orbiterName, props)
                    stationsMap[orbiterName] = stn
                    stationKeyMap[stn.key] = stn
                    orbitersMap[orbiterName] = stn
                    println("Loaded station $orbiterName with parent $parentName.")
                } else {
                    val planet = Planet(parentName, orbiterName, props)
                    planetsMap[orbiterName] = planet
                    orbitersMap[orbiterName] = planet
                    println("Loaded planet $orbiterName with parent $parentName.")
                }
                val containingFolder = it.parentFile
                val descendantFolder = File(containingFolder.path + "/" + orbiterName)
                if (descendantFolder.exists()) {
                    recursiveInitOrbiters(orbiterName, descendantFolder)
                }
            }
    }
}