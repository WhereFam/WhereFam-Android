package com.wherefam.android.data

import androidx.room.*
import com.wherefam.android.data.local.Peer
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Upsert
    suspend fun upsert(peer: Peer)

    @Delete
    suspend fun delete(peer: Peer)

    @Query("SELECT * FROM Peer ORDER BY name ASC")
    fun getAllPeers(): Flow<List<Peer>>

    @Query("SELECT * FROM Peer WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Peer?

    @Query("UPDATE Peer SET lastSeen = NULL WHERE id = :id")
    suspend fun markOffline(id: String)
}