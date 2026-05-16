package com.wherefam.android.data

import kotlinx.coroutines.flow.StateFlow

interface UserRepository {
    val currentPublicKey:  StateFlow<String>
    val pendingInviteCode: StateFlow<String>

    suspend fun requestPublicKey()
    suspend fun joinPeer(key: String)
    suspend fun leavePeer(key: String)
    suspend fun createInvite()
    suspend fun joinWithInvite(invite: String)
    suspend fun sendLocation(payload: Map<String, Any>)
    suspend fun sendPlaceEvent(payload: Map<String, Any>)
    suspend fun sendSOS(payload: Map<String, Any>)
    suspend fun sendBattery(payload: Map<String, Any>)
    suspend fun sendProfile(payload: Map<String, Any>)
    fun updatePublicKey(key: String)
    fun updateInviteCode(code: String)
}