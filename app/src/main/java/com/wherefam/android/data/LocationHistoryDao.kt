package com.wherefam.android.data

import androidx.room.*
import com.wherefam.android.data.local.LocationHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LocationHistory)

    @Query("SELECT * FROM LocationHistory WHERE peerId = :peerId ORDER BY timestamp DESC LIMIT :limit")
    fun getForPeer(peerId: String, limit: Int = 100): Flow<List<LocationHistory>>

    @Query("SELECT * FROM LocationHistory WHERE peerId = :peerId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getForPeerOnce(peerId: String, limit: Int = 100): List<LocationHistory>

    @Query("DELETE FROM LocationHistory WHERE timestamp < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM LocationHistory")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM LocationHistory WHERE peerId = :peerId")
    suspend fun countForPeer(peerId: String): Int

    @Query("DELETE FROM LocationHistory")
    suspend fun deleteAll()
}