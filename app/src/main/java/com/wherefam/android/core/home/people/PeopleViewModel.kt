package com.wherefam.android.core.home.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wherefam.android.data.PeerDao
import com.wherefam.android.data.UserRepository
import com.wherefam.android.data.local.Peer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class PeopleViewModel(
    private val userRepository: UserRepository,
    private val peerDao: PeerDao
) : ViewModel() {

    val peopleList: StateFlow<List<Peer>> = peerDao.getAllPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inviteCode: StateFlow<String> = userRepository.pendingInviteCode

    // Prevent duplicate pairing attempts
    private var isJoining = false

    fun removePerson(id: String) = viewModelScope.launch {
        peerDao.findById(id)?.let {
            peerDao.delete(it)
            userRepository.leavePeer(id)
        }
    }

    fun createInvite() = viewModelScope.launch {
        isJoining = false  // reset so a new invite can be accepted
        userRepository.createInvite()
    }

    fun joinWithInvite(invite: String) {
        if (isJoining) return  // already in progress
        isJoining = true
        viewModelScope.launch {
            try {
                userRepository.joinWithInvite(invite)
            } finally {
                // Reset after a delay to allow re-trying if it failed
                delay(5000.milliseconds)
                isJoining = false
            }
        }
    }

    fun joinPeer(key: String) = viewModelScope.launch {
        peerDao.upsert(Peer(id = key))
        userRepository.joinPeer(key)
    }
}