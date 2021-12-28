package com.dibujaron.distanthorizon.orbiter.station.hold

enum class CommodityType(val identifyingName: String, val displayName: String) {

    BIO_CELLS("biological_cells", "biological cells"),
    COPPER("copper_ore", "copper ore"),
    THORIUM("thorium"),
    DATA("encrypted_data", "encrypted data"),
    SUPERCONDUCTOR("superconductor","superconductive alloy"),
    HYDROGEN("hydrogen"),
    FOOD("food"),
    IRON("iron_ore", "iron ore"),
    LUXURIES("luxuries"),
    MACHINERY("machinery"),
    MUNITIONS("munitions"),
    WATER("water"),
    RUSH("rush", "RuSh");

    constructor(identifyingName: String): this(identifyingName, identifyingName)

    companion object {
        fun fromString(name: String): CommodityType
        {
            return values().asSequence().find { it.identifyingName == name }!!
        }
    }
}