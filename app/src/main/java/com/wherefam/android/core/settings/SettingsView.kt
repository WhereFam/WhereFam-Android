package com.wherefam.android.core.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wherefam.android.data.local.HistoryRetention
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsView(
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: SettingsViewModel = koinViewModel()
) {
    val retention    by viewModel.retention.collectAsState()
    val historyCount by viewModel.historyCount.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 8.dp))

        // History retention section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Keep location history for",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }

                Column(modifier = Modifier.selectableGroup()) {
                    HistoryRetention.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected  = retention == option,
                                    onClick   = { viewModel.setRetention(option) },
                                    role      = Role.RadioButton
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(selected = retention == option, onClick = null)
                            Text(option.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        // Storage usage
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Storage, contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    Text("Storage", style = MaterialTheme.typography.titleMedium
                        .copy(fontWeight = FontWeight.SemiBold))
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Location history points", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$historyCount", style = MaterialTheme.typography.bodyMedium
                        .copy(fontWeight = FontWeight.SemiBold))
                }

                val estKb = historyCount * 100 / 1024
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Estimated size", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (estKb < 1) "< 1 KB" else "$estKb KB",
                        style = MaterialTheme.typography.bodyMedium
                            .copy(fontWeight = FontWeight.SemiBold))
                }

                OutlinedButton(
                    onClick  = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Clear all history")
                }
            }
        }

        // Privacy note
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Lock, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(
                    "Location history is stored only on your device. " +
                    "It is never uploaded to any server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title  = { Text("Clear all history?") },
            text   = { Text("This will permanently delete all saved location history for all family members. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAllHistory(); showClearDialog = false },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}