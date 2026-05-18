package com.wherefam.android.core.home

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wherefam.android.core.people.PeopleView
import com.wherefam.android.core.places.PlacesView
import com.wherefam.android.core.safety.SafetyView
import com.wherefam.android.core.settings.SettingsView
import com.wherefam.android.manager.WhereFamLocationManager
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.ramani.compose.CameraPosition
import org.ramani.compose.rememberMapViewWithLifecycle

enum class HomeTab { Map, People, Places, Safety, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    homeViewModel: HomeViewModel = koinViewModel(),
    locationManager: WhereFamLocationManager = koinInject()
) {
    val context        = androidx.compose.ui.platform.LocalContext.current
    val peers          by homeViewModel.peers.collectAsState()
    var selectedTab    by rememberSaveable { mutableStateOf(HomeTab.Map) }
    val cameraPosition = rememberSaveable { mutableStateOf(CameraPosition(zoom = 14.0)) }
    val mapView        = rememberMapViewWithLifecycle()
    var mapReady       by remember { mutableStateOf<MapLibreMap?>(null) }

    LaunchedEffect(Unit) {
        homeViewModel.start()
        locationManager.getLocation { lat, lon ->
            cameraPosition.value = CameraPosition(target = LatLng(lat, lon), zoom = 14.0)
        }
    }

    LaunchedEffect(peers, mapReady) {
        val map = mapReady ?: return@LaunchedEffect
        map.clear()
        peers.forEach { peer ->
            val lat = peer.latitude ?: return@forEach
            val lon = peer.longitude ?: return@forEach
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(lat, lon))
                    .title(peer.name ?: peer.id.take(8))
                    .snippet(if (peer.isOnline) "Online · ${peer.lastSeenText}" else peer.lastSeenText)
            )
        }
    }

    BackHandler(enabled = selectedTab != HomeTab.Map) {
        selectedTab = HomeTab.Map
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(HomeTab.Map,      Icons.Default.Place,           "Map"),
                    Triple(HomeTab.People,   Icons.Default.Group,           "People"),
                    Triple(HomeTab.Places,   Icons.Default.LocationOn,      "Places"),
                    Triple(HomeTab.Safety,   Icons.Default.HealthAndSafety, "Safety"),
                    Triple(HomeTab.Settings, Icons.Default.Settings,        "Settings"),
                ).forEach { (tab, icon, label) ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick  = { selectedTab = tab },
                        icon     = { Icon(icon, contentDescription = label) },
                        label    = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

            // MapView kept alive via AndroidView + GONE/VISIBLE
            // GONE = not drawn, not measured, but still in memory — no reload on return
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory  = { mapView },
                update   = { view ->
                    view.visibility = if (selectedTab == HomeTab.Map) View.VISIBLE else View.GONE
                }
            )

            // Wire MapLibre only once via getMapAsync
            LaunchedEffect(mapView) {
                mapView.getMapAsync { map ->
                    map.setStyle(
                        Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
                    ) {
                        // Enable location component
                        if (map.locationComponent.isLocationComponentActivated.not()) {
                            map.locationComponent.activateLocationComponent(
                                org.maplibre.android.location.LocationComponentActivationOptions
                                    .builder(context, it)
                                    .useDefaultLocationEngine(false)
                                    .build()
                            )
                            map.locationComponent.isLocationComponentEnabled = true
                            map.locationComponent.renderMode = RenderMode.COMPASS
                        }
                        mapReady = map
                    }
                    // Move camera to user location
                    locationManager.getLocation { lat, lon ->
                        map.moveCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                LatLng(lat, lon), 14.0
                            )
                        )
                    }
                }
            }

            // Active tab content
            when (selectedTab) {
                HomeTab.People   -> PeopleView()
                HomeTab.Places   -> PlacesView()
                HomeTab.Safety   -> SafetyView()
                HomeTab.Settings -> SettingsView()
                HomeTab.Map      -> Unit
            }
        }
    }
}