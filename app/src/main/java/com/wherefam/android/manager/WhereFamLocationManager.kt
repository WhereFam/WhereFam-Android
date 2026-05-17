package com.wherefam.android.manager

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
class WhereFamLocationManager(context: Context) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val enabledProviders get() = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    ).filter { lm.isProviderEnabled(it) }

    fun getLocation(onSuccess: (Double, Double) -> Unit) {
        enabledProviders
            .mapNotNull { lm.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.let { onSuccess(it.latitude, it.longitude); return }

        val provider = enabledProviders.firstOrNull() ?: return
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lm.removeUpdates(this)
                onSuccess(location.latitude, location.longitude)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
        }
        lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
    }

    fun trackLocation(): Flow<Location> = callbackFlow {
        val listeners = mutableListOf<LocationListener>()

        enabledProviders.forEach { provider ->
            val listener = LocationListener { location -> launch { send(location) } }
            val (minTime, minDist) = when (provider) {
                LocationManager.GPS_PROVIDER     -> Pair(3_000L, 3f)
                LocationManager.NETWORK_PROVIDER -> Pair(5_000L, 10f)
                else                             -> Pair(30_000L, 50f)
            }
            try {
                lm.requestLocationUpdates(provider, minTime, minDist, listener, Looper.getMainLooper())
                listeners.add(listener)
            } catch (_: Exception) {}
        }

        enabledProviders
            .mapNotNull { lm.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
            ?.let { send(it) }

        awaitClose {
            listeners.forEach { try { lm.removeUpdates(it) } catch (_: Exception) {} }
        }
    }
}