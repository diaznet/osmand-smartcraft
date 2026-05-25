package com.diaznet.osmandsmartcraft

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import net.osmand.aidl.IOsmAndAidlInterface
import net.osmand.aidl.mapwidget.AMapWidget
import net.osmand.aidl.mapwidget.AddMapWidgetParams
import net.osmand.aidl.mapwidget.RemoveMapWidgetParams
import net.osmand.aidl.mapwidget.UpdateMapWidgetParams

class OsmAndBridge(private val context: Context) {

    companion object {
        private const val OSMAND_PACKAGE = "net.osmand"
        private const val OSMAND_SERVICE = "net.osmand.aidl.OsmandAidlService"
    }

    private var osmAndApi: IOsmAndAidlInterface? = null
    private val unitPrefs = UnitPrefs(context)

    @Volatile
    var connected = false
        private set

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            osmAndApi = IOsmAndAidlInterface.Stub.asInterface(service)
            connected = true
            log("Bound to OsmAnd service")
            registerWidgets()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            osmAndApi = null
            connected = false
            log("Disconnected from OsmAnd")
        }
    }

    private fun log(msg: String) {
        SmartCraftService.log("AIDL", msg)
    }

    fun isOsmAndConnected(): Boolean = connected

    fun connect() {
        val intent = Intent(OSMAND_SERVICE).apply { setPackage(OSMAND_PACKAGE) }
        val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        log("bindService result: $bound")
    }

    fun disconnect() {
        if (connected) {
            removeWidgets()
            context.unbindService(serviceConnection)
            connected = false
        }
    }

    fun connectAndRun(action: () -> Unit) {
        val intent = Intent(OSMAND_SERVICE).apply { setPackage(OSMAND_PACKAGE) }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                osmAndApi = IOsmAndAidlInterface.Stub.asInterface(service)
                connected = true
                action()
                context.unbindService(this)
                connected = false
            }
            override fun onServiceDisconnected(name: ComponentName?) { connected = false }
        }
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    fun clearAllWidgets() {
        widgetDefs.forEach { def ->
            try {
                val result = osmAndApi?.removeMapWidget(RemoveMapWidgetParams(def.id))
                log("removeWidget(${def.id}) = $result")
            } catch (_: Exception) {}
        }
        log("All widgets cleared")
    }

    private data class WidgetDef(val id: String, val title: String, val dayIcon: String, val nightIcon: String)

    private val widgetDefs = listOf(
        WidgetDef("smartcraft_rpm", "RPM", "widget_obd_engine_speed_day", "widget_obd_engine_speed_night"),
        WidgetDef("smartcraft_temp", "Coolant Temp", "widget_obd_temperature_coolant_day", "widget_obd_temperature_coolant_night"),
        WidgetDef("smartcraft_voltage", "Voltage", "widget_obd_battery_voltage_day", "widget_obd_battery_voltage_night"),
        WidgetDef("smartcraft_fuel_flow", "Fuel Flow", "widget_obd_fuel_consumption_day", "widget_obd_fuel_consumption_night"),
        WidgetDef("smartcraft_fuel_level", "Fuel Level", "widget_obd_fuel_remaining_day", "widget_obd_fuel_remaining_night"),
        WidgetDef("smartcraft_oil_pressure", "Oil Pressure", "widget_obd_fuel_pressure_day", "widget_obd_fuel_pressure_night"),
        WidgetDef("smartcraft_runtime", "Engine Hours", "widget_obd_engine_runtime_day", "widget_obd_engine_runtime_night"),
        WidgetDef("smartcraft_fuel_used", "Fuel Used", "widget_obd_fuel_consumption_day", "widget_obd_fuel_consumption_night"),
        WidgetDef("smartcraft_gear", "Gear", "widget_obd_engine_speed_day", "widget_obd_engine_speed_night"),
        WidgetDef("smartcraft_block_pressure", "Block Pressure", "widget_obd_fuel_pressure_day", "widget_obd_fuel_pressure_night"),
        WidgetDef("smartcraft_oil_temp", "Oil Temp", "widget_obd_temperature_coolant_day", "widget_obd_temperature_coolant_night"),
        WidgetDef("smartcraft_seawater_temp", "Sea Temp", "widget_obd_temperature_coolant_day", "widget_obd_temperature_coolant_night"),
    )

    private fun registerWidgets() {
        widgetDefs.forEachIndexed { i, def ->
            try { osmAndApi?.removeMapWidget(RemoveMapWidgetParams(def.id)) } catch (_: Exception) {}
            val widget = AMapWidget(def.id, def.dayIcon, def.title, def.dayIcon, def.nightIcon, "--", "", i + 1, null)
            try {
                val result = osmAndApi?.addMapWidget(AddMapWidgetParams(widget))
                log("addMapWidget(${def.id}) = $result")
            } catch (e: Exception) {
                log("FAILED addWidget ${def.id}: ${e.message}")
            }
        }
    }

    private fun removeWidgets() {
        widgetDefs.forEach { def ->
            try { osmAndApi?.removeMapWidget(RemoveMapWidgetParams(def.id)) } catch (_: Exception) {}
        }
    }

    fun updateData(data: SmartCraftData) {
        if (!connected) return

        val (tempVal, tempUnit) = unitPrefs.formatTemp(data.coolantTempC)
        val (pressVal, pressUnit) = unitPrefs.formatPressure(data.oilPressureKpa)
        val (blockPressVal, blockPressUnit) = unitPrefs.formatPressure(data.blockPressureKpa)
        val (flowVal, flowUnit) = unitPrefs.formatFlow(data.fuelFlowLph)
        val (oilTempVal, oilTempUnit) = unitPrefs.formatTemp(data.oilTempC)
        val (seaTempVal, seaTempUnit) = unitPrefs.formatTemp(data.seawaterTempC)

        updateWidget("smartcraft_rpm", "${data.rpm}", "RPM", 1)
        updateWidget("smartcraft_temp", tempVal, tempUnit, 2)
        updateWidget("smartcraft_voltage", "%.1f".format(data.voltageV), "V", 3)
        updateWidget("smartcraft_fuel_flow", flowVal, flowUnit, 4)
        updateWidget("smartcraft_fuel_level", "%.0f".format(data.fuelLevelPct), "%", 5)
        updateWidget("smartcraft_oil_pressure", pressVal, pressUnit, 6)
        updateWidget("smartcraft_runtime", "${data.runtimeMin / 60}h", "", 7)
        updateWidget("smartcraft_fuel_used", "${data.fuelUsed}", "", 8)
        updateWidget("smartcraft_gear", gearText(data.gear), "", 9)
        updateWidget("smartcraft_block_pressure", blockPressVal, blockPressUnit, 10)
        updateWidget("smartcraft_oil_temp", oilTempVal, oilTempUnit, 11)
        updateWidget("smartcraft_seawater_temp", seaTempVal, seaTempUnit, 12)
    }

    private fun gearText(raw: Int): String = when {
        raw == 0 -> "N"
        raw == 1 -> "F"
        raw == 2 -> "R"
        else -> raw.toString()
    }

    fun pushStale() {
        if (!connected) return
        widgetDefs.forEachIndexed { i, def ->
            updateWidget(def.id, "--", "", i + 1)
        }
    }

    private fun updateWidget(id: String, text: String, desc: String, order: Int) {
        val def = widgetDefs.find { it.id == id } ?: return
        val widget = AMapWidget(id, def.dayIcon, def.title, def.dayIcon, def.nightIcon, text, desc, order, null)
        try {
            val result = osmAndApi?.updateMapWidget(UpdateMapWidgetParams(widget))
            if (logCounter++ % 7 == 0) log("updateWidget($id)=$result text=$text")
        } catch (e: android.os.DeadObjectException) {
            log("OsmAnd died, reconnecting...")
            connected = false
            osmAndApi = null
            reconnect()
        } catch (e: Exception) {
            log("FAILED update $id: ${e.message}")
        }
    }

    private fun reconnect() {
        try { context.unbindService(serviceConnection) } catch (_: Exception) {}
        connect()
    }

    private var logCounter = 0
}
