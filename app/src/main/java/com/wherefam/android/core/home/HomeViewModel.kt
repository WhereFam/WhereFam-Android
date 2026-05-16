package com.wherefam.android.core.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wherefam.android.data.PeerDao
import com.wherefam.android.data.ipc.IPCUtils.writeAsync
import com.wherefam.android.data.local.GenericAction
import com.wherefam.android.data.local.Peer
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import to.holepunch.bare.kit.IPC
import java.nio.ByteBuffer
import java.nio.charset.Charset

class HomeViewModel(
    context: Context,
    private val ipc: IPC,
    private val peerDao: PeerDao
) : ViewModel() {

    private val fileDir = context.filesDir

    val peers: StateFlow<List<Peer>> = peerDao.getAllPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun start() {
        viewModelScope.launch {
            val data = buildJsonObject { put("path", fileDir.path) }
            val msg  = GenericAction(action = "start", data = data)
            val json = Json.encodeToString(msg) + "\n"
            ipc.writeAsync(ByteBuffer.wrap(json.toByteArray(Charset.forName("UTF-8"))))
        }
    }
}