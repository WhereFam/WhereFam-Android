package com.wherefam.android.core.home.people

import android.graphics.BitmapFactory
import android.location.Geocoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import com.wherefam.android.data.LocationHistoryDao
import com.wherefam.android.data.local.DataStoreRepository
import com.wherefam.android.data.local.LocationHistory
import com.wherefam.android.data.local.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.ramani.compose.rememberMapViewWithLifecycle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailView(
    peer:     Peer,
    onBack:   () -> Unit,
    onRemove: (Peer) -> Unit
) {
    val context              = LocalContext.current
    val historyDao: LocationHistoryDao   = koinInject()
    val dataStore: DataStoreRepository   = koinInject()

    val history by historyDao.getForPeer(peer.id, 50).collectAsState(initial = emptyList())
    var showRemoveDialog by remember { mutableStateOf(false) }

    // Load peer avatar from file
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(peer.id) {
        avatarBitmap = withContext(Dispatchers.IO) {
            val file = dataStore.getPeerImageFile(peer.id)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peer.name ?: "Person") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showRemoveDialog = true }) {
                        Icon(Icons.Default.PersonRemove, contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Mini map
            if (peer.latitude != null && peer.longitude != null) {
                PersonMiniMap(latitude = peer.latitude, longitude = peer.longitude,
                    modifier = Modifier.fillMaxWidth().height(220.dp))
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LocationOff, contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Location unavailable", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Avatar + name header
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp).offset(y = (-28).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.size(72.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center) {
                    if (avatarBitmap != null) {
                        Image(bitmap = avatarBitmap!!,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    } else {
                        Text(peer.initials,
                            style = MaterialTheme.typography.headlineSmall
                                .copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(peer.name ?: "Unknown",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(if (peer.isOnline) Color(0xFF4CAF50) else Color.Gray))
                        Text(peer.lastSeenText, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Status cards
            Column(modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-16).dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatusCard(modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        color = if (peer.isDriving) Color(0xFFFF9800) else Color(0xFF2196F3),
                        title = "Speed",
                        value = peer.speedKmh?.let { "%.0f km/h".format(it) } ?: "—")
                    StatusCard(modifier = Modifier.weight(1f),
                        icon = if (peer.batteryCharging == true) Icons.Default.BatteryChargingFull
                        else Icons.Default.Battery3Bar,
                        color = batteryColor(peer),
                        title = "Battery",
                        value = peer.batteryLevel?.let { "${(it * 100).toInt()}%" } ?: "—")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatusCard(modifier = Modifier.weight(1f),
                        icon = Icons.Default.Height,
                        color = Color(0xFF009688),
                        title = "Altitude",
                        value = peer.altitude?.let { "%.0f m".format(it) } ?: "—")
                    StatusCard(modifier = Modifier.weight(1f),
                        icon = Icons.Default.AccessTime,
                        color = Color(0xFF9C27B0),
                        title = "Last seen",
                        value = peer.lastSeenText)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // Timeline
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Timeline", style = MaterialTheme.typography.titleMedium
                    .copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 8.dp))

                if (history.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.History, contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            Text("No history yet", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    history.forEachIndexed { idx, entry ->
                        TimelineRow(entry = entry, isLast = idx == history.lastIndex)
                    }
                }
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title  = { Text("Remove ${peer.name ?: "this person"}?") },
            text   = { Text("They will no longer see your location and you won't see theirs.") },
            confirmButton = {
                TextButton(onClick = { onRemove(peer); showRemoveDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TimelineRow(entry: LocationHistory, isLast: Boolean) {
    val context = LocalContext.current
    var address by remember(entry.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(entry.id) {
        address = withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault())
                    .getFromLocation(entry.latitude, entry.longitude, 1)
                    ?.firstOrNull()?.let { addr ->
                        listOfNotNull(addr.thoroughfare, addr.locality)
                            .joinToString(", ").ifEmpty { addr.getAddressLine(0) }
                    }
            } catch (_: Exception) { null }
        }
    }

    Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Timeline line + dot
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 4.dp)) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape)
                .background(if (entry.isMoving) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary))
            if (!isLast) {
                Box(modifier = Modifier.width(2.dp).height(40.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant))
            }
        }

        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 8.dp)) {
            Text(address ?: "%.4f, %.4f".format(entry.latitude, entry.longitude),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatTime(entry.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.isMoving) {
                    Text("· %.0f km/h".format(entry.speedKmh ?: 0.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF9800))
                }
            }
        }
    }
}

private fun formatTime(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm · MMM d", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}

@Composable
fun StatusCard(modifier: Modifier = Modifier, icon: ImageVector, color: Color, title: String, value: String) {
    Column(modifier = modifier.clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(title, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PersonMiniMap(latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
    val mapView = rememberMapViewWithLifecycle()
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) {
                map.uiSettings.isScrollGesturesEnabled = false
                map.uiSettings.isZoomGesturesEnabled   = false
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled   = false
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15.0))
            }
        }
    }
    AndroidView(modifier = modifier, factory = { mapView })
}

private fun batteryColor(peer: Peer): Color {
    if (peer.batteryCharging == true) return Color(0xFF4CAF50)
    return if ((peer.batteryLevel ?: 1f) < 0.2f) Color(0xFFF44336) else Color(0xFF4CAF50)
}