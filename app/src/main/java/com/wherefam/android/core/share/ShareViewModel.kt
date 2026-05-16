package com.wherefam.android.core.share

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.wherefam.android.data.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareViewModel(private val userRepository: UserRepository) : ViewModel() {

    val publicKey: StateFlow<String> = userRepository.currentPublicKey

    // Invite flow
    val inviteCode: StateFlow<String> = userRepository.pendingInviteCode

    private val _inviteQr  = MutableStateFlow<ImageBitmap?>(null)

    private val _permanentQr = MutableStateFlow<ImageBitmap?>(null)
    val permanentQr: StateFlow<ImageBitmap?> = _permanentQr.asStateFlow()

    private val _generating = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            publicKey.collectLatest { key ->
                if (key.isNotEmpty()) _permanentQr.value = generateQr(key)
            }
        }
        viewModelScope.launch {
            inviteCode.collectLatest { code ->
                if (code.isNotEmpty()) {
                    _inviteQr.value = generateQr("wherefam://invite?code=$code")
                    _generating.value = false
                }
            }
        }
    }

    suspend fun requestPublicKey() = userRepository.requestPublicKey()

    fun createInvite() {
        _generating.value = true
        _inviteQr.value   = null
        viewModelScope.launch { userRepository.createInvite() }
    }

    fun joinWithInvite(invite: String) {
        viewModelScope.launch { userRepository.joinWithInvite(invite) }
    }

    private suspend fun generateQr(content: String): ImageBitmap =
        withContext(Dispatchers.Default) {
            val size  = 512
            val hints = hashMapOf(EncodeHintType.MARGIN to 1)
            val bits  = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp   = createBitmap(size, size, Bitmap.Config.RGB_565).also { bmp ->
                for (x in 0 until size) for (y in 0 until size)
                    bmp[x, y] = if (bits[x, y]) Color.BLACK else Color.WHITE
            }
            bmp.asImageBitmap()
        }
}