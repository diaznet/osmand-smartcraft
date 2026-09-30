package com.diaznet.osmandsmartcraft

import com.diaznet.osmandsmartcraft.UnitPrefs.EfficiencyUnit
import org.junit.Assert.*
import org.junit.Test

class FuelEfficiencyTest {

    @Test
    fun `km per liter is speed divided by flow`() {
        // 30 km/h burning 10 L/h = 3 km/L
        assertEquals(3f, FuelEfficiency.kmPerLiter(10f, 30f)!!, 0.001f)
    }

    @Test
    fun `below minimum speed returns null`() {
        assertNull(FuelEfficiency.kmPerLiter(2f, FuelEfficiency.MIN_SPEED_KMH - 0.1f))
    }

    @Test
    fun `at minimum speed returns a value`() {
        assertNotNull(FuelEfficiency.kmPerLiter(2f, FuelEfficiency.MIN_SPEED_KMH))
    }

    @Test
    fun `zero or negative flow returns null`() {
        assertNull(FuelEfficiency.kmPerLiter(0f, 30f))
        assertNull(FuelEfficiency.kmPerLiter(-1f, 30f))
    }

    @Test
    fun `missing inputs return null`() {
        assertNull(FuelEfficiency.kmPerLiter(null, 30f))
        assertNull(FuelEfficiency.kmPerLiter(10f, null))
    }

    @Test
    fun `convert to km per liter is identity`() {
        assertEquals(3f, FuelEfficiency.convert(3f, EfficiencyUnit.KM_PER_L), 0.001f)
    }

    @Test
    fun `convert to liters per 100 km`() {
        // 4 km/L = 25 L/100km
        assertEquals(25f, FuelEfficiency.convert(4f, EfficiencyUnit.L_PER_100KM), 0.001f)
    }

    @Test
    fun `convert to nautical miles per liter`() {
        // 1.852 km/L = 1 NM/L
        assertEquals(1f, FuelEfficiency.convert(1.852f, EfficiencyUnit.NM_PER_L), 0.001f)
    }

    @Test
    fun `convert to liters per nautical mile`() {
        // 0.926 km/L = 2 L/NM
        assertEquals(2f, FuelEfficiency.convert(0.926f, EfficiencyUnit.L_PER_NM), 0.001f)
    }

    @Test
    fun `convert to US mpg`() {
        // 1 km/L = 2.352 mpg (US)
        assertEquals(2.352f, FuelEfficiency.convert(1f, EfficiencyUnit.MPG_US), 0.001f)
    }
}
