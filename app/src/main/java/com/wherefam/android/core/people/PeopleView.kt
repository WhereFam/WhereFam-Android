package com.wherefam.android.core.people

import SwipeToDeleteContainer
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.wherefam.android.R
import com.wherefam.android.core.home.people.PersonDetailView
import com.wherefam.android.data.local.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleView(viewModel: PeopleViewModel = koinViewModel(), contentPadding: PaddingValues = PaddingValues()) {
    val people by viewModel.peopleList.collectAsState()
    val inviteCode by viewModel.inviteCode.collectAsState()

    var showInviteSheet by remember { mutableStateOf(false) }
    var showScanSheet by remember { mutableStateOf(false) }
    var pasteInput by remember { mutableStateOf("") }
    var showPasteDialog by remember { mutableStateOf(false) }
    var selectedPeer by remember { mutableStateOf<Peer?>(null) }

    // Only show invite sheet when code freshly generated (empty → non-empty transition)
    var lastInviteCode by remember { mutableStateOf(inviteCode) }  // init with current so tab return doesn't trigger
    LaunchedEffect(inviteCode) {
        if (inviteCode.isNotEmpty() && lastInviteCode.isEmpty()) {
            showInviteSheet = true
        }
        lastInviteCode = inviteCode
    }

    val peopleNavController = rememberNavController()

    NavHost(
        navController    = peopleNavController,
        startDestination = "list",
        modifier         = Modifier.fillMaxSize()
    ) {
        composable("list") {

            Column(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("People", style = MaterialTheme.typography.headlineMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { showScanSheet = true }) {
                            Icon(
                                painterResource(R.drawable.round_qr_code_2_24),
                                contentDescription = "Scan invite"
                            )
                        }
                        IconButton(onClick = {
                            viewModel.createInvite()
                            showInviteSheet = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add person")
                        }
                    }
                }

                if (people.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                "No people yet", style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Tap + to invite a family member",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(people, key = { it.id }) { peer ->
                                SwipeToDeleteContainer(item = peer, onDelete = {
                                    viewModel.removePerson(peer.id)
                                }) {
                                    PeerRow(peer = peer, onClick = { selectedPeer = peer; peopleNavController.navigate("detail") })
                                }
                                if (peer != people.lastOrNull())
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            }

            // Invite sheet
            if (showInviteSheet) {
                val inviteSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val inviteScope = rememberCoroutineScope()
                ModalBottomSheet(
                    onDismissRequest = {
                        inviteScope.launch { inviteSheetState.hide() }.invokeOnCompletion {
                            showInviteSheet = false
                        }
                    },
                    sheetState = inviteSheetState,
                    dragHandle = null,
                ) {
                    InviteSheet(
                        viewModel = viewModel,
                        onPasteInstead = {
                            showInviteSheet = false
                            showPasteDialog = true
                        }
                    )
                }
            }

            // Scanner sheet
            if (showScanSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showScanSheet = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    QRScannerSheet(
                        onScanned = { code ->
                            showScanSheet = false
                            val invite = if (code.startsWith("wherefam://invite?code="))
                                code.removePrefix("wherefam://invite?code=") else code
                            viewModel.joinWithInvite(invite)
                        },
                        onDismiss = { showScanSheet = false }
                    )
                }
            }

            // Paste dialog fallback
            if (showPasteDialog) {
                AlertDialog(
                    onDismissRequest = { showPasteDialog = false },
                    title = { Text("Enter invite code") },
                    text = {
                        OutlinedTextField(
                            value = pasteInput,
                            onValueChange = { pasteInput = it },
                            label = { Text("Invite code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (pasteInput.isNotBlank()) {
                                viewModel.joinWithInvite(pasteInput.trim())
                                pasteInput = ""
                                showPasteDialog = false
                            }
                        }) { Text("Join") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPasteDialog = false }) { Text("Cancel") }
                    }
                )
            }
        } // end Column
        composable("detail") {
            selectedPeer?.let { peer ->
                PersonDetailView(
                    peer = peer,
                    onRemove = { p ->
                        viewModel.removePerson(p.id)
                        peopleNavController.popBackStack()
                        selectedPeer = null
                    }
                )
            }
        }
    } // end Scaffold
} // end list composable

@Composable
fun PeerRow(peer: Peer, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = peer.initials,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    peer.name ?: peer.id.take(12),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                )
                Box(
                    modifier = Modifier.size(7.dp).clip(CircleShape)
                        .background(if (peer.isOnline) Color(0xFF4CAF50) else Color.Gray.copy(alpha = 0.4f))
                )
            }
            Text(
                text = buildString {
                    append(peer.lastSeenText)
                    if (peer.isDriving) append(" · Driving")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        peer.batteryLevel?.let { level ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = if (peer.batteryCharging == true) Icons.Default.BatteryChargingFull
                    else Icons.Default.BatteryFull,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (level < 0.2f && peer.batteryCharging != true) Color.Red
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${(level * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InviteSheet(viewModel: PeopleViewModel, onPasteInstead: () -> Unit) {
    val inviteCode by viewModel.inviteCode.collectAsState()
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Your Invite", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Share this with a family member. Single-use, expires once accepted.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (inviteCode.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Text(
                "Generating invite…", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Box(
                modifier = Modifier.size(200.dp)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                InviteQRCode(inviteCode = inviteCode)
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("invite", "wherefam://invite?code=$inviteCode")
                    )
                    copied = true
                    coroutineScope.launch {
                        delay(2000)
                        copied = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied!" else "Copy invite link")
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join my WhereFam circle: wherefam://invite?code=$inviteCode")
                        putExtra(Intent.EXTRA_SUBJECT, "Join me on WhereFam")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share invite"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send via…")
            }

            TextButton(onClick = { viewModel.createInvite() }) {
                Text(
                    "Generate new invite", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TextButton(onClick = onPasteInstead) {
            Text(
                "Enter their invite code instead",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun InviteQRCode(inviteCode: String) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(inviteCode) {
        withContext(Dispatchers.Default) {
            val content = "wherefam://invite?code=$inviteCode"
            val size = 512
            val hints = hashMapOf(EncodeHintType.MARGIN to 1)
            val bits = QRCodeWriter()
                .encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) for (y in 0 until size)
                bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            bitmap = bmp
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Invite QR",
            modifier = Modifier.size(180.dp)
        )
    } ?: CircularProgressIndicator(modifier = Modifier.size(32.dp))
}

@Composable
fun QRScannerSheet(onScanned: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier.fillMaxWidth().height(400.dp).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Scan their invite QR", style = MaterialTheme.typography.titleMedium)

        if (hasPermission) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
                CameraPreview(
                    onQrScanned = onScanned,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Text(
                "Point at their WhereFam invite code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Camera permission needed to scan QR codes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}

@Composable
fun CameraPreview(onQrScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scanned by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                val reader = MultiFormatReader().also {
                    it.setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                }
                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    if (!scanned) {
                        try {
                            val buffer = imageProxy.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining()).also { b -> buffer.get(b) }
                            val source = PlanarYUVLuminanceSource(
                                bytes,
                                imageProxy.width, imageProxy.height,
                                0, 0, imageProxy.width, imageProxy.height, false
                            )
                            val bitmap = BinaryBitmap(HybridBinarizer(source))
                            val result = reader.decodeWithState(bitmap)
                            scanned = true
                            onQrScanned(result.text)
                        } catch (_: NotFoundException) {
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            reader.reset()
                        }
                    }
                    imageProxy.close()
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}