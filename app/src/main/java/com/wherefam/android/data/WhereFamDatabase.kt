package com.wherefam.android.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wherefam.android.data.local.LocationHistory
import com.wherefam.android.data.local.Peer
import com.wherefam.android.data.local.Place

@Database(
    entities = [Peer::class, Place::class, LocationHistory::class],
    version  = 3,
    exportSchema = true
)
abstract class WhereFamDatabase : RoomDatabase() {
    abstract val peerDao:            PeerDao
    abstract val placeDao:           PlaceDao
    abstract val locationHistoryDao: LocationHistoryDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Peer ADD COLUMN altitude REAL")
        db.execSQL("ALTER TABLE Peer ADD COLUMN speed REAL")
        db.execSQL("ALTER TABLE Peer ADD COLUMN batteryLevel REAL")
        db.execSQL("ALTER TABLE Peer ADD COLUMN batteryCharging INTEGER")
        db.execSQL("ALTER TABLE Peer ADD COLUMN lastSeen INTEGER")
        db.execSQL("ALTER TABLE Peer ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS Place (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                emoji TEXT NOT NULL DEFAULT '📍',
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                radiusMetres REAL NOT NULL DEFAULT 150.0,
                notifyOnArrive INTEGER NOT NULL DEFAULT 1,
                notifyOnLeave INTEGER NOT NULL DEFAULT 1,
                createdAt INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS LocationHistory (
                id TEXT NOT NULL PRIMARY KEY,
                peerId TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL,
                speed REAL,
                accuracy REAL,
                timestamp INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_LocationHistory_peerId ON LocationHistory(peerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_LocationHistory_timestamp ON LocationHistory(timestamp)")
    }
}