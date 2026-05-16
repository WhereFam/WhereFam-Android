package com.wherefam.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.wherefam.android.core.home.HomeViewModel
import com.wherefam.android.core.onboarding.SplashViewModel
import com.wherefam.android.core.root.ContentView
import com.wherefam.android.data.UserRepository
import com.wherefam.android.data.ipc.IPCMessageConsumer
import com.wherefam.android.data.ipc.IPCProvider
import com.wherefam.android.manager.LocationTrackerService
import com.wherefam.android.processing.GenericMessageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import to.holepunch.bare.kit.IPC
import to.holepunch.bare.kit.Worklet

class MainActivity : ComponentActivity() {

    private var worklet: Worklet? = null
    private var ipc: IPC? = null
    private val messageProcessor: GenericMessageProcessor by inject()
    private val userRepository: UserRepository by inject()
    private var ipcMessageConsumer: IPCMessageConsumer? = null
    private val homeViewModel: HomeViewModel by viewModel()
    private val splashViewModel: SplashViewModel by viewModel()

    // Pending invite from deep link arriving before JS is ready
    private var pendingInvite: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()

        worklet = Worklet(null)
        try {
            worklet!!.start("/app.bundle", assets.open("app.bundle"), null)
            ipc = IPC(worklet)
            IPCProvider.ipc = ipc
            ipcMessageConsumer = IPCMessageConsumer(ipc!!, messageProcessor)
            ipcMessageConsumer?.lifecycleScope = lifecycleScope
            ipcMessageConsumer?.startConsuming()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        // Notification channels
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        listOf(
            Triple(LocationTrackerService.LOCATION_CHANNEL, "Location",       NotificationManager.IMPORTANCE_LOW),
            Triple("place_alerts",                           "Place Alerts",   NotificationManager.IMPORTANCE_DEFAULT),
            Triple("sos_alerts",                             "SOS Alerts",     NotificationManager.IMPORTANCE_HIGH),
            Triple("battery_alerts",                         "Battery Alerts", NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach { (id, name, importance) ->
            nm.createNotificationChannel(NotificationChannel(id, name, importance))
        }

        // Handle deep link invite on cold launch
        handleDeepLink(intent)

        // Once JS is ready, process any pending invite
        lifecycleScope.launch {
            userRepository.currentPublicKey.first { it.isNotEmpty() }
            pendingInvite?.let { code ->
                pendingInvite = null
                userRepository.joinWithInvite(code)
            }
        }

        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                setContent {
                    val screen by splashViewModel.startDestination
                    ContentView(rememberNavController(), screen)
                }
            }
        }
    }

    // Called for both cold launch and when app is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "wherefam") return

        when (uri.host) {
            "invite" -> {
                val code = uri.getQueryParameter("code") ?: return
                if (userRepository.currentPublicKey.value.isNotEmpty()) {
                    // JS already ready — fire immediately
                    lifecycleScope.launch { userRepository.joinWithInvite(code) }
                } else {
                    // Store for when JS boots
                    pendingInvite = code
                }
            }
            "add" -> {
                // Legacy — direct peer key
                val key = uri.getQueryParameter("id") ?: return
                if (userRepository.currentPublicKey.value.isNotEmpty()) {
                    lifecycleScope.launch { userRepository.joinPeer(key) }
                } else {
                    // Could store similarly — skip for now
                }
            }
        }
    }

    override fun onPause()   { super.onPause();   worklet?.suspend() }
    override fun onResume()  { super.onResume();  worklet?.resume() }
    override fun onDestroy() {
        super.onDestroy()
        worklet?.terminate(); worklet = null
        startService(Intent(this, LocationTrackerService::class.java).apply {
            action = LocationTrackerService.Action.STOP.name
        })
    }
}