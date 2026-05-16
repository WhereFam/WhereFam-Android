package com.wherefam.android.core.home

import android.content.Intent
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wherefam.android.core.home.people.PeopleView
import com.wherefam.android.core.home.places.PlacesView
import com.wherefam.android.core.home.safety.SafetyView
import com.wherefam.android.core.home.settings.SettingsView
import com.wherefam.android.core.home.share.ShareIDView
import com.wherefam.android.manager.LocationManager
import com.wherefam.android.manager.LocationTrackerService
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.Style
import org.ramani.compose.CameraPosition
import org.ramani.compose.LocationStyling
import org.ramani.compose.MapLibre
import org.ramani.compose.Symbol
import org.ramani.compose.rememberMapViewWithLifecycle

enum class HomeTab { Map, People, Places, Safety, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    homeViewModel: HomeViewModel = koinViewModel(),
    locationManager: LocationManager = koinInject()
) {
    val context        = LocalContext.current
    val peers          by homeViewModel.peers.collectAsState()
    var selectedTab    by remember { mutableStateOf(HomeTab.Map) }
    var showShareSheet by remember { mutableStateOf(false) }

    val cameraPosition = rememberSaveable { mutableStateOf(CameraPosition(zoom = 14.0)) }
    val renderMode     = rememberSaveable { mutableIntStateOf(RenderMode.NORMAL) }

    LaunchedEffect(Unit) {
        homeViewModel.start()
        locationManager.getLocation { lat, lon ->
            cameraPosition.value = CameraPosition(target = LatLng(lat, lon), zoom = 14.0)
        }
        context.startService(Intent(context, LocationTrackerService::class.java).apply {
            action = LocationTrackerService.Action.START.name
        })
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected   = selectedTab == HomeTab.Map,
                    onClick    = { selectedTab = HomeTab.Map },
                    icon       = { Icon(imageVector = Icons.Default.Place,       contentDescription = "Map") },
                    label      = { Text("Map") }
                )
                NavigationBarItem(
                    selected   = selectedTab == HomeTab.People,
                    onClick    = { selectedTab = HomeTab.People },
                    icon       = { Icon(imageVector = Icons.Default.Group,       contentDescription = "People") },
                    label      = { Text("People") }
                )
                NavigationBarItem(
                    selected   = selectedTab == HomeTab.Places,
                    onClick    = { selectedTab = HomeTab.Places },
                    icon       = { Icon(imageVector = Icons.Default.LocationOn,  contentDescription = "Places") },
                    label      = { Text("Places") }
                )
                NavigationBarItem(
                    selected   = selectedTab == HomeTab.Safety,
                    onClick    = { selectedTab = HomeTab.Safety },
                    icon       = { Icon(imageVector = Icons.Default.HealthAndSafety, contentDescription = "Safety") },
                    label      = { Text("Safety") }
                )
                NavigationBarItem(
                    selected   = selectedTab == HomeTab.Settings,
                    onClick    = { selectedTab = HomeTab.Settings },
                    icon       = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label      = { Text("Settings") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == HomeTab.Map) {
                SmallFloatingActionButton(onClick = { showShareSheet = true }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share ID")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Hoist MapView so it's created once and never destroyed on tab switch
            val mapView = rememberMapViewWithLifecycle()

            // All tabs are always composed — just hidden/shown instantly
            // This avoids first-render lag when switching tabs
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == HomeTab.Map) 1f else 0f)
                    .then(if (selectedTab != HomeTab.Map) Modifier.alpha(0f) else Modifier)
            ) {
                MapLibre(
                    modifier        = Modifier.fillMaxSize(),
                    styleBuilder    = Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty"),
                    cameraPosition  = cameraPosition.value,
                    locationStyling = LocationStyling(enablePulse = true, pulseColor = Color.BLUE),
                    renderMode      = renderMode.value,
                    mapView         = mapView
                ) {
                    peers.forEach { peer ->
                        if (peer.latitude != null && peer.longitude != null) {
                            Symbol(
                                center = LatLng(peer.latitude, peer.longitude),
                                size   = 5f,
                                text   = buildString {
                                    append(peer.initials)
                                    if (peer.isDriving) append(" 🚗")
                                },
                                color  = if (peer.isOnline) "#4CAF50" else "#9E9E9E"
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == HomeTab.People) 1f else -1f)
                    .then(if (selectedTab != HomeTab.People) Modifier.alpha(0f) else Modifier)
            ) { PeopleView(contentPadding = innerPadding) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == HomeTab.Places) 1f else -1f)
                    .then(if (selectedTab != HomeTab.Places) Modifier.alpha(0f) else Modifier)
            ) { PlacesView(contentPadding = innerPadding) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == HomeTab.Safety) 1f else -1f)
                    .then(if (selectedTab != HomeTab.Safety) Modifier.alpha(0f) else Modifier)
            ) { SafetyView(contentPadding = innerPadding) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(if (selectedTab == HomeTab.Settings) 1f else -1f)
                    .then(if (selectedTab != HomeTab.Settings) Modifier.alpha(0f) else Modifier)
            ) { SettingsView(contentPadding = innerPadding) }
        }
    }

    // Share ID sheet
    if (showShareSheet) {
        ModalBottomSheet(
            onDismissRequest = { showShareSheet = false },
            sheetState       = rememberModalBottomSheetState()
        ) {
            ShareIDView()
        }
    }
}