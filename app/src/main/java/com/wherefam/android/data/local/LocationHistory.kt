package com.wherefam.android.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index("peerId"), Index("timestamp")])
data class LocationHistory(
    @PrimaryKey val id:        String,
    val peerId:                String,
    val latitude:              Double,
    val longitude:             Double,
    val altitude:              Double?  = null,
    val speed:                 Double?  = null,   // m/s
    val accuracy:              Float?   = null,
    val timestamp:             Long     = System.currentTimeMillis()
) {
    val speedKmh: Double? get() = speed?.let { it * 3.6 }
    val isMoving: Boolean get() = (speed ?: 0.0) > 0.5
}