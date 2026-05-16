package com.wherefam.android.core.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import com.wherefam.android.core.permissions.PermissionDialog

@Composable
fun FourthPageView() {
    val permissions = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    val context  = LocalContext.current
    val activity = context as? Activity
    val packageName = context.packageName  // fixed: was hardcoded

    var permissionDialog    by remember { mutableStateOf(false) }
    var launchAppSettings   by remember { mutableStateOf(false) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissions.forEach { permission ->
            if (result[permission] == false) {
                if (activity != null && !shouldShowRequestPermissionRationale(activity, permission)) {
                    launchAppSettings = true
                }
                permissionDialog = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFB77F))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = "Location Icon",
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(200.dp).padding(bottom = 32.dp)
        )
        Text(
            text = "Know your family is safe",
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, color = Color.White,
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = "Allow location access to share your position with your circle",
            textAlign = TextAlign.Center, color = Color.White.copy(alpha = 0.85f),
            fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = {
                val allGranted = permissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (!allGranted) {
                    val showRationale = permissions.any { permission ->
                        activity != null && shouldShowRequestPermissionRationale(activity, permission)
                    }
                    if (showRationale) permissionDialog = true
                    else permissionsLauncher.launch(permissions)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White, contentColor = CustomOrange
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Allow Location Access", fontSize = 22.sp, modifier = Modifier.padding(vertical = 8.dp))
        }

        if (permissionDialog) {
            PermissionDialog(
                onDismiss = { permissionDialog = false },
                onConfirm = {
                    permissionDialog = false
                    if (launchAppSettings) {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null))
                            .also { context.startActivity(it) }
                        launchAppSettings = false
                    } else {
                        permissionsLauncher.launch(permissions)
                    }
                }
            )
        }
    }
}