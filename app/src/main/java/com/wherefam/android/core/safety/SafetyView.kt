package com.wherefam.android.core.safety

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun SafetyView(viewModel: SafetyViewModel = koinViewModel(), contentPadding: PaddingValues = PaddingValues()) {
    val sosState by viewModel.sosState.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(contentPadding).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Safety", style = MaterialTheme.typography.headlineMedium)

        // SOS card
        SOSCard(
            sosState = sosState,
            onHoldStart = { viewModel.triggerSOS() },
            onCancel    = { viewModel.cancelSOS() }
        )

        // Privacy info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Privacy", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                PrivacyRow(icon = Icons.Default.Lock,       text = "Location stays on your device")
                PrivacyRow(icon = Icons.Default.People,     text = "Shared only with trusted circle")
                PrivacyRow(icon = Icons.Default.CloudOff,   text = "No server ever sees your data")
            }
        }
    }
}

@Composable
fun SOSCard(
    sosState: SosState,
    onHoldStart: () -> Unit,
    onCancel:    () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue   = 1f,
        targetValue    = if (sosState is SosState.Countdown) 1.05f else 1f,
        animationSpec  = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label          = "scale"
    )

    val cardColor = when (sosState) {
        is SosState.Idle      -> Color(0xFFB71C1C)
        is SosState.Countdown -> Color(0xFFE65100)
        is SosState.Active    -> Color(0xFF1B5E20)
        is SosState.Cancelled -> Color(0xFF424242)
    }

    Card(
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (sosState) {
                is SosState.Idle -> {
                    // Pulsing ring
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.size(100.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                        )
                        Box(
                            modifier = Modifier.size(80.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SOS", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                    Text("Emergency SOS", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Sends your live location to everyone in your circle",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center)
                    // Long-press button
                    Button(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onHoldStart() })
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor   = Color(0xFFB71C1C)
                        )
                    ) {
                        Text("Hold to send SOS", fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                is SosState.Countdown -> {
                    // Countdown ring
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                        CircularProgressIndicator(
                            progress = { sosState.seconds / 5f },
                            modifier = Modifier.fillMaxSize(),
                            color    = Color.White,
                            strokeWidth = 6.dp
                        )
                        Text(
                            text  = "${sosState.seconds}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                    Text("Sending SOS…", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    Button(
                        onClick = onCancel,
                        colors  = ButtonDefaults.buttonColors(
                            containerColor = Color.White, contentColor = Color(0xFFE65100)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                is SosState.Active -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(64.dp))
                    Text("SOS sent", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Your circle has been alerted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f))
                }

                is SosState.Cancelled -> {
                    Icon(Icons.Default.Cancel, contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(64.dp))
                    Text("SOS cancelled", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PrivacyRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}