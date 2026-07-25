package com.diaznet.osmandsmartcraft

import android.content.Context

class UnitPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("smartcraft", Context.MODE_PRIVATE)

    enum class TempUnit { CELSIUS, FAHRENHEIT }
    enum class PressureUnit { KPA, PSI, BAR }
    enum class FlowUnit { LPH, GPH }
    enum class OsmAndTarget { AUTO, OSMAND, OSMAND_PLUS }

    var tempUnit: TempUnit
        get() = if (prefs.getString("temp_unit", "C") == "F") TempUnit.FAHRENHEIT else TempUnit.CELSIUS
        set(v) = prefs.edit().putString("temp_unit", if (v == TempUnit.FAHRENHEIT) "F" else "C").apply()

    var pressureUnit: PressureUnit
        get() = when (prefs.getString("pressure_unit", "kPa")) {
            "PSI" -> PressureUnit.PSI
            "bar" -> PressureUnit.BAR
            else -> PressureUnit.KPA
        }
        set(v) = prefs.edit().putString("pressure_unit", when (v) {
            PressureUnit.PSI -> "PSI"
            PressureUnit.BAR -> "bar"
            PressureUnit.KPA -> "kPa"
        }).apply()

    var flowUnit: FlowUnit
        get() = if (prefs.getString("flow_unit", "Lph") == "GPH") FlowUnit.GPH else FlowUnit.LPH
        set(v) = prefs.edit().putString("flow_unit", if (v == FlowUnit.GPH) "GPH" else "Lph").apply()

    var osmAndTarget: OsmAndTarget
        get() = when (prefs.getString("osmand_target", "auto")) {
            "osmand" -> OsmAndTarget.OSMAND
            "osmand_plus" -> OsmAndTarget.OSMAND_PLUS
            else -> OsmAndTarget.AUTO
        }
        set(v) = prefs.edit().putString("osmand_target", when (v) {
            OsmAndTarget.OSMAND -> "osmand"
            OsmAndTarget.OSMAND_PLUS -> "osmand_plus"
            OsmAndTarget.AUTO -> "auto"
        }).apply()

    fun formatTemp(celsius: Float): Pair<String, String> = when (tempUnit) {
        TempUnit.CELSIUS -> "%.0f".format(celsius) to "°C"
        TempUnit.FAHRENHEIT -> "%.0f".format(celsius * 9f / 5f + 32f) to "°F"
    }

    fun formatPressure(kpa: Float): Pair<String, String> = when (pressureUnit) {
        PressureUnit.KPA -> "%.0f".format(kpa) to "kPa"
        PressureUnit.PSI -> "%.1f".format(kpa * 0.145038f) to "PSI"
        PressureUnit.BAR -> "%.2f".format(kpa / 100f) to "bar"
    }

    fun formatFlow(lph: Float): Pair<String, String> = when (flowUnit) {
        FlowUnit.LPH -> "%.2f".format(lph) to "L/h"
        FlowUnit.GPH -> "%.2f".format(lph * 0.264172f) to "gal/h"
    }
}
