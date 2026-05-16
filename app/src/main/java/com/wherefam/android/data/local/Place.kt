package com.wherefam.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Place(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String            = "📍",
    val latitude: Double,
    val longitude: Double,
    val radiusMetres: Double     = 150.0,
    val notifyOnArrive: Boolean  = true,
    val notifyOnLeave: Boolean   = true,
    val createdAt: Long          = System.currentTimeMillis()
)