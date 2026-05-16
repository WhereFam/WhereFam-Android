package com.wherefam.android.core.home.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wherefam.android.data.PlaceDao
import com.wherefam.android.data.UserRepository
import com.wherefam.android.data.local.Place
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PlacesViewModel(
    private val placeDao: PlaceDao,
    private val userRepository: UserRepository
) : ViewModel() {

    val places: StateFlow<List<Place>> = placeDao.getAllPlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addPlace(place: Place) = viewModelScope.launch { placeDao.upsert(place) }

    fun deletePlace(place: Place) = viewModelScope.launch { placeDao.delete(place) }
}