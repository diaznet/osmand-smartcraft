package com.diaznet.osmandsmartcraft

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.core.location.LocationListenerCompat

/** GPS speed over ground, used to derive fuel efficiency. */
@SuppressLint("MissingPermission")
class SpeedProvider(context: Context) {

    companion object {
        private const val UPDATE_INTERVAL_MS = 1000L
        /** A fix older than this is treated as no speed. */
        private const val MAX_FIX_AGE_MS = 5000L
    }

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @Volatile private var lastSpeedKmh: Float? = null
    @Volatile private var lastFixElapsedMs = 0L

    private val listener = object : LocationListenerCompat {
        override fun onLocationChanged(location: Location) {
            if (!location.hasSpeed()) return
            lastSpeedKmh = location.speed * 3.6f
            lastFixElapsedMs = SystemClock.elapsedRealtime()
        }
        override fun onProviderEnabled(provider: String) { log("GPS enabled") }
        override fun onProviderDisabled(provider: String) { log("GPS disabled — no speed, efficiency unavailable") }
    }

    fun start() {
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            log("GPS provider disabled — efficiency unavailable until enabled")
        }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, 0f, listener, Looper.getMainLooper()
            )
            log("GPS speed updates started")
        } catch (e: Exception) {
            log("FAILED to start GPS: ${e.message}")
        }
    }

    fun stop() {
        try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
        lastSpeedKmh = null
    }

    fun currentSpeedKmh(): Float? =
        if (SystemClock.elapsedRealtime() - lastFixElapsedMs <= MAX_FIX_AGE_MS) lastSpeedKmh else null

    private fun log(msg: String) = SmartCraftService.log("DATA", msg)
}
