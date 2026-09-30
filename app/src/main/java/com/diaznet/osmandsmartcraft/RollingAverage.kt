package com.diaznet.osmandsmartcraft

/** Mean of the samples added within the last [windowMs]. Not thread-safe. */
class RollingAverage(private val windowMs: Long) {

    private val samples = ArrayDeque<Pair<Long, Float>>()

    fun add(value: Float, timestampMs: Long) {
        samples.addLast(timestampMs to value)
        prune(timestampMs)
    }

    fun average(nowMs: Long): Float? {
        prune(nowMs)
        if (samples.isEmpty()) return null
        return (samples.sumOf { it.second.toDouble() } / samples.size).toFloat()
    }

    private fun prune(nowMs: Long) {
        while (samples.isNotEmpty() && nowMs - samples.first().first > windowMs) samples.removeFirst()
    }
}
