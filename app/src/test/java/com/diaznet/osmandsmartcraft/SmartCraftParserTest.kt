package com.diaznet.osmandsmartcraft

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SmartCraftParserTest {

    private val parser = SmartCraftParser()

    private fun makePayload(value: Int): ByteArray {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0x00) // padding byte 0
        buf.put(0x00) // padding byte 1
        buf.putShort(value.toShort()) // UInt16LE at offset 2
        return buf.array()
    }

    @Test
    fun `parse RPM characteristic`() {
        val data = makePayload(2500)
        val result = parser.parseCharacteristic(SmartCraftParser.RPM_UUID, data)
        assertEquals(2500, result.rpm)
    }

    @Test
    fun `parse coolant temp characteristic`() {
        val data = makePayload(85)
        val result = parser.parseCharacteristic(SmartCraftParser.COOLANT_TEMP_UUID, data)
        assertEquals(85f, result.coolantTempC, 0.01f)
    }

    @Test
    fun `parse voltage characteristic`() {
        val data = makePayload(12600) // 12600 / 1000 = 12.6V
        val result = parser.parseCharacteristic(SmartCraftParser.VOLTAGE_UUID, data)
        assertEquals(12.6f, result.voltageV, 0.01f)
    }

    @Test
    fun `parse runtime characteristic`() {
        val data = makePayload(120) // 120 minutes
        val result = parser.parseCharacteristic(SmartCraftParser.RUNTIME_UUID, data)
        assertEquals(120, result.runtimeMin)
    }

    @Test
    fun `parse fuel flow characteristic`() {
        val data = makePayload(500) // 500 / 100000 * 1000 = 5.0 L/h
        val result = parser.parseCharacteristic(SmartCraftParser.FUEL_FLOW_UUID, data)
        assertEquals(5.0f, result.fuelFlowLph, 0.01f)
    }

    @Test
    fun `parse fuel level characteristic`() {
        val data = makePayload(7500) // 7500 / 100 = 75%
        val result = parser.parseCharacteristic(SmartCraftParser.FUEL_LEVEL_UUID, data)
        assertEquals(75f, result.fuelLevelPct, 0.01f)
    }

    @Test
    fun `parse oil pressure characteristic`() {
        val data = makePayload(35000) // 35000 / 100 = 350 kPa
        val result = parser.parseCharacteristic(SmartCraftParser.OIL_PRESSURE_UUID, data)
        assertEquals(350f, result.oilPressureKpa, 0.01f)
    }

    @Test
    fun `parser accumulates values across calls`() {
        parser.parseCharacteristic(SmartCraftParser.RPM_UUID, makePayload(1000))
        parser.parseCharacteristic(SmartCraftParser.VOLTAGE_UUID, makePayload(13200))
        val result = parser.getCurrent()
        assertEquals(1000, result.rpm)
        assertEquals(13.2f, result.voltageV, 0.01f)
    }

    @Test
    fun `short payload returns zero`() {
        val data = byteArrayOf(0x00) // too short
        val result = parser.parseCharacteristic(SmartCraftParser.RPM_UUID, data)
        assertEquals(0, result.rpm)
    }

    @Test
    fun `unknown UUID does not change state`() {
        parser.parseCharacteristic(SmartCraftParser.RPM_UUID, makePayload(3000))
        val result = parser.parseCharacteristic("00000199-0000-1000-8000-ec55f9f5b963", makePayload(9999))
        assertEquals(3000, result.rpm)
    }
}
