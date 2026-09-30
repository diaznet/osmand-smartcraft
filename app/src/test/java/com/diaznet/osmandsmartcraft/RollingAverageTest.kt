package com.diaznet.osmandsmartcraft

import org.junit.Assert.*
import org.junit.Test

class RollingAverageTest {

    @Test
    fun `empty average is null`() {
        assertNull(RollingAverage(10_000).average(0))
    }

    @Test
    fun `averages samples in window`() {
        val avg = RollingAverage(10_000)
        avg.add(10f, 1_000)
        avg.add(20f, 2_000)
        avg.add(30f, 3_000)
        assertEquals(20f, avg.average(3_000)!!, 0.001f)
    }

    @Test
    fun `drops samples older than window`() {
        val avg = RollingAverage(10_000)
        avg.add(100f, 0)
        avg.add(10f, 5_000)
        avg.add(20f, 10_001) // pushes the t=0 sample out
        assertEquals(15f, avg.average(10_001)!!, 0.001f)
    }

    @Test
    fun `becomes null once all samples expire`() {
        val avg = RollingAverage(10_000)
        avg.add(10f, 0)
        assertNull(avg.average(10_001))
    }
}
