package com.wherefam.android.core.home

import android.graphics.*
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
import com.wherefam.android.data.local.DataStoreRepository
import com.wherefam.android.manager.WhereFamLocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
import org.maplibre.android.style.layers.Property.TEXT_ANCHOR_TOP
import org.ramani.compose.rememberMapViewWithLifecycle

enum class HomeTab { Map, People, Places, Safety, Settings }

private const val PEER_ICON = "peer-avatar"

private fun drawPeerAvatar(initials: String, isOnline: Boolean, photo: Bitmap? = null): Bitmap {
    val size = 96
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    if (photo != null) {
        // Clip photo to circle
        val shader = BitmapShader(
            Bitmap.createScaledBitmap(photo, size, size, true),
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setShader(shader)
        })
    } else {
        // Initials circle
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOnline) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
        })
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(initials.take(2).uppercase(), size / 2f, size / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
    }

    // White border ring
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 4f
    })
    // Online dot bottom-right
    val dotRadius = 10f
    val dotX = size - dotRadius - 2f
    val dotY = size - dotRadius - 2f
    canvas.drawCircle(dotX, dotY, dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isOnline) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
    })
    canvas.drawCircle(dotX, dotY, dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f
    })

    return bmp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    homeViewModel: HomeViewModel = koinViewModel(),
    locationManager: WhereFamLocationManager = koinInject(),
    dataStore: DataStoreRepository = koinInject()
) {
    val context        = androidx.compose.ui.platform.LocalContext.current
    val peers          by homeViewModel.peers.collectAsState()
    var selectedTab    by rememberSaveable { mutableStateOf(HomeTab.Map) }
    val mapView        = rememberMapViewWithLifecycle()

    // SymbolManager + symbol map — same pattern as old version
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }
    val peerSymbols   = remember { mutableStateMapOf<String, Symbol>() }
    var mapRef        by remember { mutableStateOf<MapLibreMap?>(null) }

    LaunchedEffect(Unit) {
        homeViewModel.start()
    }

    // Update symbols whenever peers or symbolManager changes
    LaunchedEffect(peers, symbolManager) {
        val sm  = symbolManager ?: return@LaunchedEffect
        val map = mapRef        ?: return@LaunchedEffect

        val currentIds = peers.map { it.id }.toSet()
        (peerSymbols.keys - currentIds).forEach { id ->
            peerSymbols[id]?.let { sm.delete(it) }
            peerSymbols.remove(id)
        }

        peers.forEach { peer ->
            val lat = peer.latitude  ?: return@forEach
            val lon = peer.longitude ?: return@forEach
            val latLng = LatLng(lat, lon)
            val label  = buildString {
                append(peer.name ?: peer.id.take(8))
                if (peer.isDriving) append(" 🚗")
            }
            val imgKey = "avatar_${peer.id}"

            // Always register/refresh the avatar image in the style
            val style = map.style
            if (style != null) {
                val photo = withContext(Dispatchers.IO) {
                    val file = dataStore.getPeerImageFile(peer.id)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
                }
                style.addImage(imgKey, drawPeerAvatar(peer.initials, peer.isOnline, photo))
            }

            val existing = peerSymbols[peer.id]
            if (existing != null) {
                existing.latLng    = latLng
                existing.textField = label
                sm.update(existing)
            } else {
                val symbol = sm.create(
                    SymbolOptions()
                        .withLatLng(latLng)
                        .withIconImage(imgKey)
                        .withIconAnchor(ICON_ANCHOR_BOTTOM)
                        .withIconSize(1.0f)
                        .withTextField(label)
                        .withTextSize(14f)
                        .withTextFont(arrayOf("Noto Sans Regular"))
                        .withTextAnchor(TEXT_ANCHOR_TOP)
                        .withTextOffset(arrayOf(0f, 0.8f))
                )
                if (symbol != null) peerSymbols[peer.id] = symbol
            }
        }
    }

    BackHandler(enabled = selectedTab != HomeTab.Map) {
        selectedTab = HomeTab.Map
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(HomeTab.Map,      Icons.Default.Map,             "Map"),
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
        Box(modifier = Modifier.fillMaxSize()) {

            AndroidView(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                factory  = { mapView },
                update   = { view ->
                    view.visibility = if (selectedTab == HomeTab.Map) View.VISIBLE else View.GONE
                }
            )

            LaunchedEffect(mapView) {
                mapView.getMapAsync { map ->
                    map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) { style ->

                        // Blue dot
                        if (!map.locationComponent.isLocationComponentActivated) {
                            map.locationComponent.activateLocationComponent(
                                LocationComponentActivationOptions.builder(context, style)
                                    .useDefaultLocationEngine(false).build()
                            )
                            map.locationComponent.isLocationComponentEnabled = true
                            map.locationComponent.renderMode = RenderMode.COMPASS
                        }

                        // Camera to user location
                        locationManager.getLocation { lat, lon ->
                            map.moveCamera(
                                org.maplibre.android.camera.CameraUpdateFactory
                                    .newLatLngZoom(LatLng(lat, lon), 14.0)
                            )
                        }

                        // Init SymbolManager — triggers LaunchedEffect(peers, symbolManager)
                        mapRef = map
                        symbolManager = SymbolManager(mapView, map, style).apply {
                            iconAllowOverlap = true
                            textAllowOverlap = true
                        }
                    }
                }
            }

            when (selectedTab) {
                HomeTab.People   -> PeopleView(contentPadding = innerPadding)
                HomeTab.Places   -> PlacesView(contentPadding = innerPadding)
                HomeTab.Safety   -> SafetyView(contentPadding = innerPadding)
                HomeTab.Settings -> SettingsView(contentPadding = innerPadding)
                HomeTab.Map      -> Unit
            }
        }
    }
}