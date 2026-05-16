package com.wherefam.android.core.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wherefam.android.data.UserRepository
import com.wherefam.android.data.local.DataStoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

sealed class SosState {
    object Idle      : SosState()
    data class Countdown(val seconds: Int) : SosState()
    object Active    : SosState()
    object Cancelled : SosState()
}

class SafetyViewModel(
    private val userRepository: UserRepository,
    private val dataStoreRepository: DataStoreRepository
) : ViewModel() {

    private val _sosState = MutableStateFlow<SosState>(SosState.Idle)
    val sosState: StateFlow<SosState> = _sosState.asStateFlow()

    private var countdownJob: Job? = null

    fun triggerSOS() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 5 downTo 1) {
                _sosState.value = SosState.Countdown(i)
                delay(1000.milliseconds)
            }
            _sosState.value = SosState.Active
            sendSOS()
        }
    }

    fun cancelSOS() {
        countdownJob?.cancel()
        _sosState.value = SosState.Cancelled
        viewModelScope.launch {
            delay(2000.milliseconds)
            _sosState.value = SosState.Idle
        }
    }

    private suspend fun sendSOS() {
        val key  = userRepository.currentPublicKey.value
        val name = dataStoreRepository.getUserName()
        userRepository.sendSOS(mapOf(
            "id"        to key,
            "name"      to name,
            "type"      to "manual",
            "timestamp" to System.currentTimeMillis().toDouble()
        ))
    }
}