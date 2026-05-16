package com.wherefam.android.core.home.places

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

data class PickedCoordinate(val latitude: Double, val longitude: Double)

@SuppressLint("MissingPermission")
fun getLastKnownLocation(context: Context): Pair<Double, Double> {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )
    for (provider in providers) {
        try {
            val loc = lm.getLastKnownLocation(provider)
            if (loc != null) return Pair(loc.latitude, loc.longitude)
        } catch (_: Exception) {}
    }
    // fallback — San Francisco
    return Pair(37.7749, -122.4194)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoordinatePickerView(
    initialLatitude:  Double? = null,
    initialLongitude: Double? = null,
    onConfirm: (PickedCoordinate) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle

    val (startLat, startLon) = remember {
        if (initialLatitude != null && initialLongitude != null)
            Pair(initialLatitude, initialLongitude)
        else getLastKnownLocation(context)
    }

    var pickedLat by remember { mutableStateOf(startLat) }
    var pickedLon by remember { mutableStateOf(startLon) }

    // Create MapView manually — full lifecycle control
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    // Mirror lifecycle events to MapView
    DisposableEffect(lifecycle) {
        val observer = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner)  { mapView.onStart() }
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) { mapView.onResume() }
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner)  { mapView.onPause() }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner)   { mapView.onStop() }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()  // fully destroy when picker exits
        }
    }

    // Wire map once
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.setStyle(
                Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
            ) {
                map.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(startLat, startLon), 15.0)
                )
                map.addOnCameraIdleListener {
                    map.cameraPosition.target?.let { target ->
                        pickedLat = target.latitude
                        pickedLon = target.longitude
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Drop a pin") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onConfirm(PickedCoordinate(pickedLat, pickedLon))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirm",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Map — factory only, no update block so it's never touched after init
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory  = { mapView }
            )

            // Fixed center pin
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.offset(y = (-20).dp) // offset up by half pin height
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Pin",
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    // Shadow dot at pin base
                    Surface(
                        modifier = Modifier.size(6.dp),
                        shape    = CircleShape,
                        color    = Color.Black.copy(alpha = 0.25f)
                    ) {}
                }
            }

            // Coordinate card + confirm button at bottom
            Surface(
                modifier       = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape          = MaterialTheme.shapes.large,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Selected location",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "%.5f, %.5f".format(pickedLat, pickedLon),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Button(
                        onClick  = { onConfirm(PickedCoordinate(pickedLat, pickedLon)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Use this location")
                    }
                }
            }
        }
    }
}