package com.wherefam.android.data.ipc

import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.wherefam.android.data.ipc.IPCUtils.readStream
import com.wherefam.android.processing.MessageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import to.holepunch.bare.kit.IPC

class IPCMessageConsumer(
    private val ipc: IPC,
    private val messageProcessor: MessageProcessor
) {
    var lifecycleScope: LifecycleCoroutineScope? = null

    fun startConsuming() {
        lifecycleScope?.launch(Dispatchers.IO) {
            val buffer = StringBuilder()
            ipc.readStream().collect { chunk ->
                buffer.append(chunk)
                // Process all complete newline-delimited messages
                // Same logic as the JS ipc.js ipcBuffer accumulator
                var newlineIndex = buffer.indexOf('\n')
                while (newlineIndex != -1) {
                    val line = buffer.substring(0, newlineIndex).trim()
                    buffer.delete(0, newlineIndex + 1)
                    if (line.isNotEmpty()) {
                        messageProcessor.processMessage(line)
                    }
                    newlineIndex = buffer.indexOf('\n')
                }
            }
        } ?: Log.e("IPCMessageConsumer", "lifecycleScope is null")
    }
}