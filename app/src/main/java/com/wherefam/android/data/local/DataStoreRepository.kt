package com.wherefam.android.data.local

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_info")

enum class HistoryRetention(val label: String, val days: Int?) {
    ONE_DAY(   "1 day",    1),
    THREE_DAYS("3 days",   3),
    ONE_WEEK(  "1 week",   7),
    TWO_WEEKS( "2 weeks",  14),
    ONE_MONTH( "1 month",  30),
    FOREVER(   "Forever",  null)
}

class DataStoreRepository(private val context: Context) {

    private companion object {
        val USER_NAME             = stringPreferencesKey("user_name")
        val ONBOARDING_COMPLETED  = booleanPreferencesKey("onboarding_completed")
        val HISTORY_RETENTION     = stringPreferencesKey("history_retention")
        const val USER_IMAGE_FILE = "user_profile_image.jpg"
    }

    // ── Profile image ──────────────────────────────────────────────
    suspend fun saveUserImage(bitmap: Bitmap?) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, USER_IMAGE_FILE)
            if (bitmap != null) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("DataStore", "Error saving image: ${e.message}")
        }
    }

    fun getUserImageFile(): File = File(context.filesDir, USER_IMAGE_FILE)

    fun getPeerImageFile(peerId: String): File {
        val dir = File(context.filesDir, "avatars").also { it.mkdirs() }
        return File(dir, "$peerId.jpg")
    }

    suspend fun savePeerImage(peerId: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(getPeerImageFile(peerId)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        } catch (e: Exception) {
            Log.e("DataStore", "Error saving peer image: ${e.message}")
        }
    }

    // ── User name ──────────────────────────────────────────────────
    suspend fun saveUserName(value: String) {
        context.userPreferencesDataStore.edit { it[USER_NAME] = value }
    }

    suspend fun getUserName(): String =
        context.userPreferencesDataStore.data.first()[USER_NAME] ?: ""

    // ── Onboarding ─────────────────────────────────────────────────
    suspend fun saveOnboardingState(completed: Boolean) {
        context.userPreferencesDataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    fun readOnBoardingState(): Flow<Boolean> =
        context.userPreferencesDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[ONBOARDING_COMPLETED] ?: false }

    // ── History retention ──────────────────────────────────────────
    suspend fun saveHistoryRetention(retention: HistoryRetention) {
        context.userPreferencesDataStore.edit { it[HISTORY_RETENTION] = retention.name }
    }

    suspend fun getHistoryRetention(): HistoryRetention {
        val name = context.userPreferencesDataStore.data.first()[HISTORY_RETENTION]
            ?: HistoryRetention.THREE_DAYS.name
        return HistoryRetention.valueOf(name)
    }

    fun historyRetentionFlow(): Flow<HistoryRetention> =
        context.userPreferencesDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                val name = prefs[HISTORY_RETENTION] ?: HistoryRetention.THREE_DAYS.name
                HistoryRetention.valueOf(name)
            }
}