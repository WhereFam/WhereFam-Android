package com.wherefam.android.core.home

import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wherefam.android.core.home.people.PeopleView
import com.wherefam.android.core.places.PlacesView
import com.wherefam.android.core.safety.SafetyView
import com.wherefam.android.core.settings.SettingsView
import com.wherefam.android.data.local.Peer
import com.wherefam.android.manager.WhereFamLocationManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.ramani.compose.CameraPosition
import org.ramani.compose.LocationStyling
import org.ramani.compose.MapLibre
import org.ramani.compose.rememberMapViewWithLifecycle

private fun addPeerMarkersToStyle(style: Style, mapView: MapView, peers: List<Peer>) {
    mapView.getMapAsync { map ->
        map.clear()
        peers.forEach { peer ->
            val lat = peer.latitude ?: return@forEach
            val lon = peer.longitude ?: return@forEach
            val label = buildString {
                append(peer.initials)
                if (peer.isDriving) append(" 🚗")
            }
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(lat, lon))
                    .title(peer.name ?: peer.id.take(8))
                    .snippet(if (peer.isOnline) "Online" else peer.lastSeenText)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    homeViewModel: HomeViewModel = koinViewModel(),
    locationManager: WhereFamLocationManager = koinInject()
) {
    val context        = androidx.compose.ui.platform.LocalContext.current
    val peers          by homeViewModel.peers.collectAsState()
    val navController  = rememberNavController()
    val currentRoute   = navController.currentBackStackEntryAsState().value?.destination?.route

    val cameraPosition = rememberSaveable { mutableStateOf(CameraPosition(zoom = 14.0)) }
    val renderMode     = rememberSaveable { mutableIntStateOf(RenderMode.NORMAL) }

    // Hoist mapView so it survives tab switches — only the Compose wrapper recreates
    val mapView = rememberMapViewWithLifecycle()

    LaunchedEffect(Unit) {
        homeViewModel.start()
        locationManager.getLocation { lat, lon ->
            cameraPosition.value = CameraPosition(target = LatLng(lat, lon), zoom = 14.0)
        }
    }

    BackHandler(enabled = currentRoute != "map") {
        navController.navigate("map") {
            popUpTo("map") { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple("map",      Icons.Default.Place,           "Map"),
                    Triple("people",   Icons.Default.Group,           "People"),
                    Triple("places",   Icons.Default.LocationOn,      "Places"),
                    Triple("safety",   Icons.Default.HealthAndSafety, "Safety"),
                    Triple("settings", Icons.Default.Settings,        "Settings"),
                ).forEach { (route, icon, label) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick  = {
                            if (currentRoute != route) {
                                navController.navigate(route) {
                                    popUpTo("map") { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        },
                        icon  = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = "map",
            modifier         = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable("map") {
                val currentPeers by rememberUpdatedState(peers)
                var mapReady by remember { mutableStateOf<MapLibreMap?>(null) }

                // Update markers whenever peers change and map is ready
                LaunchedEffect(currentPeers, mapReady) {
                    val map = mapReady ?: return@LaunchedEffect
                    map.clear()
                    currentPeers.forEach { peer ->
                        val lat = peer.latitude ?: return@forEach
                        val lon = peer.longitude ?: return@forEach
                        map.addMarker(
                            MarkerOptions()
                                .position(LatLng(lat, lon))
                                .title(peer.name ?: peer.id.take(8))
                                .snippet(if (peer.isOnline) "Online · ${peer.lastSeenText}"
                                else peer.lastSeenText)
                        )
                    }
                }

                MapLibre(
                    modifier        = Modifier.fillMaxSize(),
                    styleBuilder    = Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty"),
                    cameraPosition  = cameraPosition.value,
                    locationStyling = LocationStyling(enablePulse = true, pulseColor = Color.BLUE),
                    renderMode      = renderMode.intValue,
                    mapView         = mapView,
                    onStyleLoaded   = { mapView.getMapAsync { mapReady = it } }
                )
            }
            composable("people")   { PeopleView(contentPadding = innerPadding) }
            composable("places")   { PlacesView(contentPadding = innerPadding) }
            composable("safety")   { SafetyView(contentPadding = innerPadding) }
            composable("settings") { SettingsView(contentPadding = innerPadding) }
        }
    }
}