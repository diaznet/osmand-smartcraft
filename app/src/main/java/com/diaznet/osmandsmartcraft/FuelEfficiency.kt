package com.diaznet.osmandsmartcraft

/**
 * Fuel efficiency derived from engine fuel flow and GPS speed over ground.
 * Base unit is km/L; [convert] maps it to the user's display unit.
 */
object FuelEfficiency {

    /** Below ~2 kn (idle, drifting, docking) efficiency is meaningless and shown as "--". */
    const val MIN_SPEED_KMH = 4f

    private const val KM_PER_NM = 1.852f
    private const val KM_PER_L_TO_MPG_US = 2.352146f

    fun kmPerLiter(fuelFlowLph: Float?, speedKmh: Float?): Float? {
        if (fuelFlowLph == null || speedKmh == null) return null
        if (speedKmh < MIN_SPEED_KMH || fuelFlowLph <= 0f) return null
        return speedKmh / fuelFlowLph
    }

    fun convert(kmPerL: Float, unit: UnitPrefs.EfficiencyUnit): Float = when (unit) {
        UnitPrefs.EfficiencyUnit.KM_PER_L -> kmPerL
        UnitPrefs.EfficiencyUnit.L_PER_100KM -> 100f / kmPerL
        UnitPrefs.EfficiencyUnit.NM_PER_L -> kmPerL / KM_PER_NM
        UnitPrefs.EfficiencyUnit.L_PER_NM -> KM_PER_NM / kmPerL
        UnitPrefs.EfficiencyUnit.MPG_US -> kmPerL * KM_PER_L_TO_MPG_US
    }
}
