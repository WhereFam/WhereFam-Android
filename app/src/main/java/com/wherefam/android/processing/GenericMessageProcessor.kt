package com.wherefam.android.processing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wherefam.android.R
import com.wherefam.android.data.LocationHistoryDao
import com.wherefam.android.data.PeerDao
import com.wherefam.android.data.PlaceDao
import com.wherefam.android.data.UserRepository
import com.wherefam.android.data.local.DataStoreRepository
import com.wherefam.android.data.local.GenericAction
import com.wherefam.android.data.local.LocationHistory
import com.wherefam.android.data.local.Peer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID
import kotlin.math.*

class GenericMessageProcessor(
    private val context:           Context,
    private val userRepository:    UserRepository,
    private val peerDao:           PeerDao,
    private val placeDao:          PlaceDao,
    private val locationHistoryDao: LocationHistoryDao,
    private val dataStoreRepository: DataStoreRepository
) : MessageProcessor {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Save-side throttle — last saved position per peer
    private data class LastSaved(val lat: Double, val lon: Double, val time: Long)
    private val lastSaved = mutableMapOf<String, LastSaved>()

    // Trim job scope
    private val trimScope = CoroutineScope(Dispatchers.IO)

    init {
        createNotificationChannels()
        runRetentionTrim()
    }

    override suspend fun processMessage(message: String) {
        val line = message.trim()
        if (line.isEmpty()) return
        try {
            val msg = Json.decodeFromString<GenericAction>(line)
            when (msg.action) {
                "ready"            -> handleReady(msg)
                "publicKeyResponse"-> handlePublicKey(msg)
                "inviteCreated"    -> handleInviteCreated(msg)
                "peerPaired"       -> handlePeerPaired(msg)
                "locationUpdate"   -> handleLocationUpdate(msg)
                "peerDisconnected" -> handlePeerDisconnected(msg)
                "placeEvent"       -> handlePlaceEvent(msg)
                "sosAlert"         -> handleSosAlert(msg)
                "batteryUpdate"    -> handleBatteryUpdate(msg)
                "profileSync"      -> handleProfileSync(msg)
                "startupError"     -> Log.e("IPC", "JS error: ${msg.data}")
                else               -> Log.w("IPC", "Unknown: ${msg.action}")
            }
        } catch (e: Exception) {
            Log.e("IPC", "Error processing: ${e.message}")
        }
    }

    private fun handleReady(msg: GenericAction) {
        val key = msg.data?.jsonObject?.get("publicKey")?.jsonPrimitive?.content ?: return
        userRepository.updatePublicKey(key)
        Log.d("IPC", "JS ready, pk: ${key.take(12)}")
    }

    private fun handlePublicKey(msg: GenericAction) {
        val key = msg.data?.jsonObject?.get("publicKey")?.jsonPrimitive?.content ?: return
        userRepository.updatePublicKey(key)
    }

    private fun handleInviteCreated(msg: GenericAction) {
        val invite = msg.data?.jsonObject?.get("invite")?.jsonPrimitive?.content ?: return
        userRepository.updateInviteCode(invite)
    }

    private suspend fun handlePeerPaired(msg: GenericAction) {
        val key = msg.data?.jsonObject?.get("peerKey")?.jsonPrimitive?.content ?: return
        if (peerDao.findById(key) == null) peerDao.upsert(Peer(id = key))
    }

    private suspend fun handleLocationUpdate(msg: GenericAction) {
        val d   = msg.data?.jsonObject ?: return
        val id  = d["id"]?.jsonPrimitive?.content ?: return
        val lat = d["latitude"]?.jsonPrimitive?.doubleOrNull ?: return
        val lon = d["longitude"]?.jsonPrimitive?.doubleOrNull ?: return
        val spd = d["speed"]?.jsonPrimitive?.doubleOrNull
        val acc = d["accuracy"]?.jsonPrimitive?.floatOrNull

        // Accuracy filter on receive side too
        if (acc != null && acc > 35f) return

        // Update peer in Room
        val existing = peerDao.findById(id)
        peerDao.upsert((existing ?: Peer(id = id)).copy(
            name            = d["name"]?.jsonPrimitive?.contentOrNull ?: existing?.name,
            latitude        = lat,
            longitude       = lon,
            altitude        = d["altitude"]?.jsonPrimitive?.doubleOrNull,
            speed           = spd,
            batteryLevel    = d["batteryLevel"]?.jsonPrimitive?.floatOrNull,
            batteryCharging = d["batteryCharging"]?.jsonPrimitive?.booleanOrNull,
            lastSeen        = d["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis()
        ))

        // Save-side throttle for history
        maybeSaveHistory(id, lat, lon, spd, d["altitude"]?.jsonPrimitive?.doubleOrNull, acc,
            d["timestamp"]?.jsonPrimitive?.longOrNull ?: System.currentTimeMillis())

        // Check peer location against saved places
        checkPeerAgainstPlaces(id, lat, lon,
            d["name"]?.jsonPrimitive?.contentOrNull ?: id.take(8))
    }

    private suspend fun maybeSaveHistory(
        peerId: String, lat: Double, lon: Double,
        speed: Double?, altitude: Double?, accuracy: Float?, timestamp: Long
    ) {
        val now  = System.currentTimeMillis()
        val last = lastSaved[peerId]

        // Adaptive save thresholds based on speed
        val (distThreshold, timeThreshold) = when {
            (speed ?: 0.0) > 4.2  -> Pair(10.0,  5_000L)   // driving
            (speed ?: 0.0) > 0.5  -> Pair(5.0,   10_000L)  // walking
            else                   -> Pair(15.0,  120_000L) // stationary
        }

        val distMoved = if (last != null)
            haversineMetres(last.lat, last.lon, lat, lon) else Double.MAX_VALUE
        val timePassed = if (last != null) now - last.time else Long.MAX_VALUE

        if (distMoved < distThreshold && timePassed < timeThreshold) return

        lastSaved[peerId] = LastSaved(lat, lon, now)
        locationHistoryDao.insert(LocationHistory(
            id        = UUID.randomUUID().toString(),
            peerId    = peerId,
            latitude  = lat,
            longitude = lon,
            altitude  = altitude,
            speed     = speed,
            accuracy  = accuracy,
            timestamp = timestamp
        ))
    }

    private suspend fun handlePeerDisconnected(msg: GenericAction) {
        val key = msg.data?.jsonObject?.get("peerKey")?.jsonPrimitive?.content ?: return
        peerDao.markOffline(key)
    }

    private fun handlePlaceEvent(msg: GenericAction) {
        val d         = msg.data?.jsonObject ?: return
        val name      = d["name"]?.jsonPrimitive?.contentOrNull ?: "Someone"
        val event     = d["event"]?.jsonPrimitive?.contentOrNull ?: return
        val placeName = d["placeName"]?.jsonPrimitive?.contentOrNull ?: return
        val emoji     = d["emoji"]?.jsonPrimitive?.contentOrNull ?: "📍"
        val title     = if (event == "arrived") "$emoji $name arrived at $placeName"
        else "$emoji $name left $placeName"
        notify(CHANNEL_PLACES, "place-$name", title, "")
    }

    private fun handleSosAlert(msg: GenericAction) {
        val d    = msg.data?.jsonObject ?: return
        val name = d["name"]?.jsonPrimitive?.contentOrNull ?: "Someone"
        val type = d["type"]?.jsonPrimitive?.contentOrNull ?: "manual"
        val title = if (type == "crash") "🚨 $name may have been in a crash"
        else "🆘 $name sent an SOS"
        notify(CHANNEL_SOS, "sos-$name", title, "Tap to see their location", highPriority = true)
    }

    private suspend fun handleBatteryUpdate(msg: GenericAction) {
        val d        = msg.data?.jsonObject ?: return
        val id       = d["id"]?.jsonPrimitive?.contentOrNull ?: return
        val level    = d["batteryLevel"]?.jsonPrimitive?.floatOrNull ?: return
        val charging = d["batteryCharging"]?.jsonPrimitive?.booleanOrNull
        peerDao.findById(id)?.let { peerDao.upsert(it.copy(batteryLevel = level, batteryCharging = charging)) }
        if (level <= 0.2f && charging != true) {
            val name = d["name"]?.jsonPrimitive?.contentOrNull ?: "Someone"
            notify(CHANNEL_BATTERY, "battery-$id", "🔋 $name's battery is low",
                "${(level * 100).toInt()}% remaining")
        }
    }

    private suspend fun handleProfileSync(msg: GenericAction) {
        val d    = msg.data?.jsonObject ?: return
        val id   = d["id"]?.jsonPrimitive?.contentOrNull ?: return
        val name = d["name"]?.jsonPrimitive?.contentOrNull

        // Update peer name
        val existing = peerDao.findById(id) ?: Peer(id = id)
        peerDao.upsert(existing.copy(name = name ?: existing.name))

        // Save avatar if present
        val b64 = d["avatarData"]?.jsonPrimitive?.contentOrNull ?: return
        try {
            val bytes  = Base64.decode(b64, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            dataStoreRepository.savePeerImage(id, bitmap)
            Log.d("IPC", "Saved avatar for peer ${id.take(12)}")
        } catch (e: Exception) {
            Log.e("IPC", "Failed to save avatar: ${e.message}")
        }
    }

    private suspend fun checkPeerAgainstPlaces(peerId: String, lat: Double, lon: Double, name: String) {
        placeDao.getAllPlacesOnce().forEach { place ->
            val dist = haversineMetres(lat, lon, place.latitude, place.longitude)
            if (dist <= place.radiusMetres && place.notifyOnArrive) {
                notify(CHANNEL_PLACES, "peer-arrive-$peerId-${place.id}",
                    "${place.emoji} $name arrived at ${place.name}", "")
            }
        }
    }

    private fun runRetentionTrim() {
        trimScope.launch {
            val retention = dataStoreRepository.getHistoryRetention()
            val days      = retention.days ?: return@launch
            val cutoff    = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            locationHistoryDao.deleteOlderThan(cutoff)
            Log.d("IPC", "Trimmed history older than ${retention.label}")
        }
    }

    private fun haversineMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r    = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat/2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1-a))
    }

    private fun notify(channel: String, id: String, title: String, body: String, highPriority: Boolean = false) {
        notificationManager.notify(id.hashCode(), NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.baseline_location_on_24)
            .setContentTitle(title).setContentText(body)
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true).build())
    }

    private fun createNotificationChannels() {
        listOf(
            Triple(CHANNEL_PLACES,  "Place Alerts",   NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CHANNEL_SOS,     "SOS Alerts",     NotificationManager.IMPORTANCE_HIGH),
            Triple(CHANNEL_BATTERY, "Battery Alerts", NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach { (id, name, importance) ->
            notificationManager.createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }

    companion object {
        const val CHANNEL_PLACES  = "place_alerts"
        const val CHANNEL_SOS     = "sos_alerts"
        const val CHANNEL_BATTERY = "battery_alerts"
    }
}