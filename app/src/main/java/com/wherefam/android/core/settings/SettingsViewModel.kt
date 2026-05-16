package com.wherefam.android.core.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wherefam.android.data.LocationHistoryDao
import com.wherefam.android.data.local.DataStoreRepository
import com.wherefam.android.data.local.HistoryRetention
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dataStoreRepository:  DataStoreRepository,
    private val locationHistoryDao:   LocationHistoryDao
) : ViewModel() {

    val retention: StateFlow<HistoryRetention> = dataStoreRepository.historyRetentionFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryRetention.THREE_DAYS)

    private val _historyCount = MutableStateFlow(0)
    val historyCount: StateFlow<Int> = _historyCount.asStateFlow()

    init { refreshCount() }

    fun setRetention(r: HistoryRetention) = viewModelScope.launch {
        dataStoreRepository.saveHistoryRetention(r)
        // Trim immediately when user reduces retention
        r.days?.let { days ->
            val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
            locationHistoryDao.deleteOlderThan(cutoff)
        }
        refreshCount()
    }

    fun clearAllHistory() = viewModelScope.launch {
        locationHistoryDao.deleteAll()
        refreshCount()
    }

    private fun refreshCount() = viewModelScope.launch {
        _historyCount.value = locationHistoryDao.totalCount()
    }
}