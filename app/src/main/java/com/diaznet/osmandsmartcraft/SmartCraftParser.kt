package com.diaznet.osmandsmartcraft

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses Mercury SmartCraft BLE data.
 * Based on: https://github.com/naugehyde/bt-sensors-plugin-sk/blob/main/sensor_classes/MercurySmartcraft.js
 */
data class SmartCraftData(
    val rpm: Int = 0,
    val coolantTempC: Float = 0f,
    val voltageV: Float = 0f,
    val fuelUsed: Int = 0,
    val runtimeMin: Int = 0,
    val fuelFlowLph: Float = 0f,
    val fuelLevelPct: Float = 0f,
    val gear: Int = 0,
    val oilPressureKpa: Float = 0f,
    val blockPressureKpa: Float = 0f,
    val oilTempC: Float = 0f,
    val seawaterTempC: Float = 0f
)

class SmartCraftParser {

    companion object {
        const val SDP_SERVICE_UUID = "00000000-0000-1000-8000-ec55f9f5b963"
        const val SDP_CHARACTERISTIC_UUID = "00000001-0000-1000-8000-ec55f9f5b963"
        const val DATA_SERVICE_UUID = "00000100-0000-1000-8000-ec55f9f5b963"

        const val RPM_UUID = "00000102-0000-1000-8000-ec55f9f5b963"
        const val COOLANT_TEMP_UUID = "00000103-0000-1000-8000-ec55f9f5b963"
        const val VOLTAGE_UUID = "00000104-0000-1000-8000-ec55f9f5b963"
        const val FUEL_USED_UUID = "00000105-0000-1000-8000-ec55f9f5b963"
        const val RUNTIME_UUID = "00000106-0000-1000-8000-ec55f9f5b963"
        const val FUEL_FLOW_UUID = "00000107-0000-1000-8000-ec55f9f5b963"
        const val FUEL_LEVEL_UUID = "00000108-0000-1000-8000-ec55f9f5b963"
        const val GEAR_UUID = "00000109-0000-1000-8000-ec55f9f5b963"
        const val OIL_PRESSURE_UUID = "0000010a-0000-1000-8000-ec55f9f5b963"
        const val BLOCK_PRESSURE_UUID = "0000010b-0000-1000-8000-ec55f9f5b963"
        const val OIL_TEMP_UUID = "0000010c-0000-1000-8000-ec55f9f5b963"
        const val SEAWATER_TEMP_UUID = "0000010d-0000-1000-8000-ec55f9f5b963"

        val ENABLE_DATA_CMD = byteArrayOf(0x0D, 0x01)
        private const val VALUE_OFFSET = 2
    }

    private var current = SmartCraftData()

    private fun readValue(data: ByteArray): Int {
        if (data.size < VALUE_OFFSET + 2) return 0
        return ByteBuffer.wrap(data, VALUE_OFFSET, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }

    fun parseCharacteristic(uuid: String, data: ByteArray): SmartCraftData {
        val raw = readValue(data)

        current = when (uuid.lowercase()) {
            RPM_UUID -> current.copy(rpm = raw)
            COOLANT_TEMP_UUID -> current.copy(coolantTempC = raw.toFloat())
            VOLTAGE_UUID -> current.copy(voltageV = raw / 1000f)
            FUEL_USED_UUID -> current.copy(fuelUsed = raw)
            RUNTIME_UUID -> current.copy(runtimeMin = raw)
            FUEL_FLOW_UUID -> current.copy(fuelFlowLph = raw / 100000f * 1000f)
            FUEL_LEVEL_UUID -> current.copy(fuelLevelPct = raw / 100f)
            GEAR_UUID -> current.copy(gear = raw)
            OIL_PRESSURE_UUID -> current.copy(oilPressureKpa = raw / 100f)
            BLOCK_PRESSURE_UUID -> current.copy(blockPressureKpa = raw / 100f)
            OIL_TEMP_UUID -> current.copy(oilTempC = raw.toFloat())
            SEAWATER_TEMP_UUID -> current.copy(seawaterTempC = raw.toFloat())
            else -> current
        }

        return current
    }

    fun getCurrent(): SmartCraftData = current
}
