package com.diaznet.osmandsmartcraft

import org.junit.Assert.*
import org.junit.Test

class UnitConversionTest {

    @Test
    fun `celsius to fahrenheit conversion`() {
        // 100°C = 212°F
        val f = 100f * 9f / 5f + 32f
        assertEquals(212f, f, 0.01f)
    }

    @Test
    fun `zero celsius to fahrenheit`() {
        val f = 0f * 9f / 5f + 32f
        assertEquals(32f, f, 0.01f)
    }

    @Test
    fun `kpa to psi conversion`() {
        // 100 kPa = 14.5038 PSI
        val psi = 100f * 0.145038f
        assertEquals(14.5f, psi, 0.1f)
    }

    @Test
    fun `kpa to bar conversion`() {
        // 100 kPa = 1.0 bar
        val bar = 100f / 100f
        assertEquals(1.0f, bar, 0.001f)
    }

    @Test
    fun `lph to gph conversion`() {
        // 1 L/h = 0.264172 gal/h
        val gph = 1f * 0.264172f
        assertEquals(0.264f, gph, 0.001f)
    }

    @Test
    fun `typical oil pressure conversion`() {
        // 350 kPa = 50.76 PSI = 3.5 bar
        assertEquals(50.76f, 350f * 0.145038f, 0.1f)
        assertEquals(3.5f, 350f / 100f, 0.001f)
    }
}
