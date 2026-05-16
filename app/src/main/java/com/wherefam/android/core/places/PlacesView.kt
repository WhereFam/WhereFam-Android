package com.wherefam.android.core.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wherefam.android.data.local.Place
import org.koin.androidx.compose.koinViewModel
import java.util.*

// Mirror of iOS presets
private data class PlacePreset(
    val name:   String,
    val emoji:  String,
    val icon:   ImageVector,
    val color:  Color
)

private val presets = listOf(
    PlacePreset("Home",        "🏠", Icons.Default.Home,          Color(0xFF2196F3)),
    PlacePreset("Work",        "🏢", Icons.Default.Work,          Color(0xFF3F51B5)),
    PlacePreset("School",      "🏫", Icons.Default.School,        Color(0xFF9C27B0)),
    PlacePreset("Gym",         "💪", Icons.Default.FitnessCenter, Color(0xFFFF5722)),
    PlacePreset("Hospital",    "🏥", Icons.Default.LocalHospital, Color(0xFFF44336)),
    PlacePreset("Park",        "🌳", Icons.Default.Park,          Color(0xFF4CAF50)),
    PlacePreset("Supermarket", "🛒", Icons.Default.ShoppingCart,  Color(0xFF009688)),
    PlacePreset("Restaurant",  "🍜", Icons.Default.Restaurant,    Color(0xFFFF9800)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesView(
    viewModel: PlacesViewModel = koinViewModel(),
    contentPadding: PaddingValues = PaddingValues()
) {
    val places by viewModel.places.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) {
        AddPlaceScreen(
            onSave    = { place -> viewModel.addPlace(place); showAdd = false },
            onDismiss = { showAdd = false }
        )
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Places", style = MaterialTheme.typography.headlineMedium)
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add place")
            }
        }

        if (places.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.LocationOn, contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    Text("No saved places",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text("Save places like Home or School to get notified when you arrive or leave.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    Button(onClick = { showAdd = true }) { Text("Add a place") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(places, key = { it.id }) { place ->
                    PlaceRow(
                        place    = place,
                        onDelete = { viewModel.deletePlace(place) }
                    )
                    if (place != places.lastOrNull())
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }


}

@Composable
fun PlaceRow(place: Place, onDelete: () -> Unit) {
    val preset = presets.firstOrNull { it.name == place.name }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { showDeleteDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                .background((preset?.color ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = preset?.icon ?: Icons.Default.LocationOn,
                contentDescription = null,
                tint = preset?.color ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(place.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (place.notifyOnArrive) {
                    Text("Arrive", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50))
                }
                if (place.notifyOnLeave) {
                    Text("Leave", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800))
                }
                val radiusLabel = if (place.radiusMetres >= 1000)
                    "· ${"%.1f".format(place.radiusMetres / 1000)}km"
                else "· ${place.radiusMetres.toInt()}m"
                Text(radiusLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete ${place.name}?") },
            text    = { Text("This place and its alerts will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); showDeleteDialog = false },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddPlaceScreen(onSave: (Place) -> Unit, onDismiss: () -> Unit) {
    var selectedPreset  by remember { mutableStateOf<PlacePreset?>(presets[0]) }
    var isCustom        by remember { mutableStateOf(false) }
    var customName      by remember { mutableStateOf("") }
    var latStr          by remember { mutableStateOf("") }
    var lonStr          by remember { mutableStateOf("") }
    var radius          by remember { mutableStateOf(150f) }
    var notifyArrive    by remember { mutableStateOf(true) }
    var notifyLeave     by remember { mutableStateOf(true) }
    var showPicker      by remember { mutableStateOf(false) }

    val context            = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager       = LocalFocusManager.current
    val (defaultLat, defaultLon) = remember { getLastKnownLocation(context) }

    val placeName  = if (isCustom) customName else selectedPreset?.name ?: ""
    val placeEmoji = if (isCustom) "📍" else selectedPreset?.emoji ?: "📍"
    val canSave    = placeName.isNotBlank() &&
            latStr.toDoubleOrNull()?.let { it >= -90  && it <= 90  } == true &&
            lonStr.toDoubleOrNull()?.let { it >= -180 && it <= 180 } == true

    // Full-screen map picker
    if (showPicker) {
        CoordinatePickerView(
            initialLatitude  = latStr.toDoubleOrNull() ?: defaultLat,
            initialLongitude = lonStr.toDoubleOrNull() ?: defaultLon,
            onConfirm = { coord ->
                latStr = "%.6f".format(coord.latitude)
                lonStr = "%.6f".format(coord.longitude)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Place") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // Preset grid
            Text("What kind of place?", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val selected = !isCustom && selectedPreset?.name == preset.name
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) preset.color.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedPreset = preset; isCustom = false }
                            .padding(vertical = 10.dp)
                    ) {
                        Icon(preset.icon, contentDescription = null,
                            tint = if (selected) preset.color
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(preset.name, style = MaterialTheme.typography.labelSmall,
                            color = if (selected) preset.color
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1)
                    }
                }
                // Custom
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCustom) Color(0xFF2196F3).copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { isCustom = true; selectedPreset = null }
                        .padding(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null,
                        tint = if (isCustom) Color(0xFF2196F3)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Custom", style = MaterialTheme.typography.labelSmall,
                        color = if (isCustom) Color(0xFF2196F3)
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (isCustom) {
                OutlinedTextField(
                    value = customName, onValueChange = { customName = it },
                    label = { Text("Place name") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            HorizontalDivider()

            // Location
            Text("Where is it?", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Drop pin button
            OutlinedButton(
                onClick  = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    showPicker = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (latStr.isNotEmpty() && lonStr.isNotEmpty())
                    "📍 ${"%.4f".format(latStr.toDoubleOrNull() ?: 0.0)}, ${"%.4f".format(lonStr.toDoubleOrNull() ?: 0.0)}"
                else "Drop pin on map")
            }

            Text("or enter manually", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = latStr, onValueChange = { latStr = it },
                    label = { Text("Latitude") }, placeholder = { Text("37.7749") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f), singleLine = true
                )
                OutlinedTextField(
                    value = lonStr, onValueChange = { lonStr = it },
                    label = { Text("Longitude") }, placeholder = { Text("-122.4194") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f), singleLine = true
                )
            }

            HorizontalDivider()

            // Alerts
            Text("Alerts", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null,
                        tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Text("When I arrive")
                }
                Switch(checked = notifyArrive, onCheckedChange = { notifyArrive = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null,
                        tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    Text("When I leave")
                }
                Switch(checked = notifyLeave, onCheckedChange = { notifyLeave = it })
            }

            HorizontalDivider()

            // Radius — in its own section so keyboard never overlaps it
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Detection radius", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (radius >= 1000) "${"%.1f".format(radius / 1000)} km"
                else "${radius.toInt()} m",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Slider(
                value = radius, onValueChange = { radius = it },
                valueRange = 50f..1000f,
                modifier = Modifier.fillMaxWidth()
            )
            Text("150m works well for most places.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

            Button(
                onClick = {
                    onSave(Place(
                        id             = UUID.randomUUID().toString(),
                        name           = placeName,
                        emoji          = placeEmoji,
                        latitude       = latStr.toDouble(),
                        longitude      = lonStr.toDouble(),
                        radiusMetres   = radius.toDouble(),
                        notifyOnArrive = notifyArrive,
                        notifyOnLeave  = notifyLeave
                    ))
                },
                enabled  = canSave,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save place") }
        }
    } // end Scaffold content
}