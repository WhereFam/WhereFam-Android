package com.wherefam.android.data

import androidx.room.*
import com.wherefam.android.data.local.Place
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Upsert
    suspend fun upsert(place: Place)

    @Delete
    suspend fun delete(place: Place)

    @Query("SELECT * FROM Place ORDER BY name ASC")
    fun getAllPlaces(): Flow<List<Place>>

    @Query("SELECT * FROM Place")
    suspend fun getAllPlacesOnce(): List<Place>
}