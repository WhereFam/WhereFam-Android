package com.wherefam.android.data.ipc

import com.wherefam.android.data.UserRepository
import com.wherefam.android.data.ipc.IPCUtils.writeAsync
import com.wherefam.android.data.local.GenericAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import to.holepunch.bare.kit.IPC
import java.nio.ByteBuffer
import java.nio.charset.Charset

class UserRepositoryImpl(private val ipc: IPC) : UserRepository {

    private val _publicKey  = MutableStateFlow("")
    private val _inviteCode = MutableStateFlow("")

    override val currentPublicKey:  StateFlow<String> = _publicKey.asStateFlow()
    override val pendingInviteCode: StateFlow<String> = _inviteCode.asStateFlow()

    override fun updatePublicKey(key: String)   { _publicKey.value  = key }
    override fun updateInviteCode(code: String) { _inviteCode.value = code }

    override suspend fun requestPublicKey()  = send("requestPublicKey",  buildJsonObject {})
    override suspend fun createInvite()      = send("createInvite",      buildJsonObject {})

    override suspend fun joinPeer(key: String) = send("joinPeer", buildJsonObject {
        put("peerPublicKey", key)
    })
    override suspend fun leavePeer(key: String) = send("leavePeer", buildJsonObject {
        put("peerPublicKey", key)
    })
    override suspend fun joinWithInvite(invite: String) = send("joinWithInvite", buildJsonObject {
        put("invite", invite)
    })
    override suspend fun sendLocation(payload: Map<String, Any>) =
        send("locationUpdate", payload.toJsonObject())
    override suspend fun sendPlaceEvent(payload: Map<String, Any>) =
        send("placeEvent", payload.toJsonObject())
    override suspend fun sendSOS(payload: Map<String, Any>) =
        send("sosAlert", payload.toJsonObject())
    override suspend fun sendBattery(payload: Map<String, Any>) =
        send("batteryUpdate", payload.toJsonObject())
    override suspend fun sendProfile(payload: Map<String, Any>) =
        send("saveProfile", payload.toJsonObject())

    private suspend fun send(action: String, data: JsonObject) {
        val msg  = GenericAction(action = action, data = data)
        val json = Json.encodeToString(msg) + "\n"
        ipc.writeAsync(ByteBuffer.wrap(json.toByteArray(Charset.forName("UTF-8"))))
    }

    private fun Map<String, Any>.toJsonObject(): JsonObject = buildJsonObject {
        forEach { (k, v) ->
            when (v) {
                is String  -> put(k, v)
                is Double  -> put(k, v)
                is Float   -> put(k, v.toDouble())
                is Int     -> put(k, v)
                is Long    -> put(k, v)
                is Boolean -> put(k, v)
                else       -> put(k, v.toString())
            }
        }
    }
}