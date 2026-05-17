package com.wherefam.android.manager

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wherefam.android.R
import com.wherefam.android.data.PlaceDao
import com.wherefam.android.data.UserRepository
import com.wherefam.android.data.local.DataStoreRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject
import kotlin.math.*

class LocationTrackerService : Service() {

    private val scope               = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locationManager:      WhereFamLocationManager     by inject()
    private val userRepository:       UserRepository      by inject()
    private val dataStoreRepository:  DataStoreRepository by inject()
    private val placeDao:             PlaceDao            by inject()

    // Place detection state
    private val insidePlaceIds = mutableSetOf<String>()

    // Send-side throttle state
    private var lastSentLat:   Double = 0.0
    private var lastSentLon:   Double = 0.0
    private var lastSentTime:  Long   = 0L
    private var profileSent:   Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Action.START.name -> start()
            Action.STOP.name  -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun start() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, LOCATION_CHANNEL)
            .setSmallIcon(R.drawable.baseline_location_on_24)
            .setContentTitle("WhereFam")
            .setContentText("Sharing location with your circle")
            .setOngoing(true)

        startForeground(1, notification.build())

        scope.launch {
            // Wait for JS ready
            userRepository.currentPublicKey.first { it.isNotEmpty() }

            locationManager.trackLocation().collect { location ->
                val publicKey = userRepository.currentPublicKey.value
                if (publicKey.isEmpty()) return@collect

                // Accuracy filter — skip noisy GPS readings
                if (location.hasAccuracy() && location.accuracy > 30f) return@collect

                val speedMs    = maxOf(location.speed.toDouble(), 0.0)
                val now        = System.currentTimeMillis()
                val distMoved  = haversineMetres(lastSentLat, lastSentLon, location.latitude, location.longitude)

                // Adaptive thresholds based on movement state
                val (distThreshold, timeThreshold) = when {
                    speedMs > 4.2  -> Pair(20.0,  5_000L)   // driving  >15km/h — 20m or 5s
                    speedMs > 0.5  -> Pair(8.0,   8_000L)   // walking        — 8m or 8s
                    else           -> Pair(10.0,  60_000L)  // stationary     — 10m or 60s
                }

                val shouldSend = distMoved >= distThreshold || (now - lastSentTime) >= timeThreshold

                if (!shouldSend) return@collect

                lastSentLat  = location.latitude
                lastSentLon  = location.longitude
                lastSentTime = now

                val battery  = getBatteryInfo()
                val userName = dataStoreRepository.getUserName()

                userRepository.sendLocation(mapOf(
                    "id"              to publicKey,
                    "name"            to userName,
                    "latitude"        to location.latitude,
                    "longitude"       to location.longitude,
                    "altitude"        to location.altitude,
                    "speed"           to speedMs,
                    "accuracy"        to location.accuracy.toDouble(),
                    "batteryLevel"    to battery.first,
                    "batteryCharging" to battery.second,
                    "timestamp"       to now.toDouble()
                ))

                // Send profile once on first location (ensures peers get our name/avatar)
                if (!profileSent) {
                    profileSent = true
                    sendProfile(publicKey, userName)
                }

                checkOwnLocation(location.latitude, location.longitude, userName, publicKey)

                notificationManager.notify(1, notification
                    .setContentText("Sharing · ${speedMs.times(3.6).let { "%.0f km/h".format(it) }}")
                    .build())
            }
        }
    }

    private suspend fun sendProfile(publicKey: String, name: String) {
        val imageFile = dataStoreRepository.getUserImageFile()
        if (!imageFile.exists()) {
            userRepository.sendProfile(mapOf("id" to publicKey, "name" to name))
            return
        }
        try {
            val bytes  = imageFile.readBytes()
            // Resize to max 200×200 before sending
            val bmp    = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 200, 200, true)
            val stream = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, stream)
            val b64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
            userRepository.sendProfile(mapOf(
                "id"         to publicKey,
                "name"       to name,
                "avatarData" to b64
            ))
        } catch (e: Exception) {
            userRepository.sendProfile(mapOf("id" to publicKey, "name" to name))
        }
    }

    private suspend fun checkOwnLocation(lat: Double, lon: Double, name: String, id: String) {
        placeDao.getAllPlacesOnce().forEach { place ->
            val dist     = haversineMetres(lat, lon, place.latitude, place.longitude)
            val wasInside = insidePlaceIds.contains(place.id)
            val isInside  = dist <= place.radiusMetres
            if (isInside && !wasInside) {
                insidePlaceIds.add(place.id)
                if (place.notifyOnArrive) {
                    sendLocalNotification("${place.emoji} Arrived at ${place.name}")
                    userRepository.sendPlaceEvent(mapOf(
                        "id" to id, "name" to name, "event" to "arrived",
                        "placeName" to place.name, "emoji" to place.emoji,
                        "timestamp" to System.currentTimeMillis().toDouble()
                    ))
                }
            } else if (!isInside && wasInside) {
                insidePlaceIds.remove(place.id)
                if (place.notifyOnLeave) {
                    sendLocalNotification("${place.emoji} Left ${place.name}")
                    userRepository.sendPlaceEvent(mapOf(
                        "id" to id, "name" to name, "event" to "left",
                        "placeName" to place.name, "emoji" to place.emoji,
                        "timestamp" to System.currentTimeMillis().toDouble()
                    ))
                }
            }
        }
    }

    private fun getBatteryInfo(): Pair<Double, Boolean> {
        val intent   = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level    = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale    = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status   = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct      = if (level >= 0 && scale > 0) level.toDouble() / scale else 1.0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        return Pair(pct, charging)
    }

    private fun sendLocalNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(text.hashCode(), NotificationCompat.Builder(this, "place_alerts")
            .setSmallIcon(R.drawable.baseline_location_on_24)
            .setContentTitle(text).setAutoCancel(true).build())
    }

    private fun haversineMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat/2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1-a))
    }

    private fun stop() { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    enum class Action { START, STOP }
    companion object { const val LOCATION_CHANNEL = "location_channel" }
}