package com.wherefam.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Peer(
    @PrimaryKey val id: String,
    val name: String?             = null,
    val latitude: Double?         = null,
    val longitude: Double?        = null,
    val altitude: Double?         = null,
    val speed: Double?            = null,
    val batteryLevel: Float?      = null,
    val batteryCharging: Boolean? = null,
    val lastSeen: Long?           = null,
    val addedAt: Long             = System.currentTimeMillis()
) {
    val isOnline: Boolean get() {
        val last = lastSeen ?: return false
        return System.currentTimeMillis() - last < 5 * 60 * 1000L
    }

    val isDriving: Boolean get() = (speed ?: 0.0) > 4.2

    val speedKmh: Double? get() = speed?.let { it * 3.6 }

    val initials: String get() {
        val n = name ?: return id.take(2).uppercase()
        return n.split(" ").take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
    }

    val lastSeenText: String get() {
        val last = lastSeen ?: return "Never"
        val diff = System.currentTimeMillis() - last
        return when {
            diff < 60_000L     -> "Just now"
            diff < 3_600_000L  -> "${diff / 60_000}m ago"
            diff < 86_400_000L -> "${diff / 3_600_000}h ago"
            else               -> "${diff / 86_400_000}d ago"
        }
    }
}