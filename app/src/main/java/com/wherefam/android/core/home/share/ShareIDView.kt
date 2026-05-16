package com.wherefam.android.core.home.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wherefam.android.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun ShareIDView(shareViewModel: ShareViewModel = koinViewModel()) {
    val permanentQr by shareViewModel.permanentQr.collectAsState()
    val publicKey   by shareViewModel.publicKey.collectAsState()
    val context     = LocalContext.current

    LaunchedEffect(Unit) {
        if (publicKey.isEmpty()) shareViewModel.requestPublicKey()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        CustomToolbar(
            title       = "Share Your ID",
            onShareClick = { sharePublicKey(context, publicKey) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        permanentQr?.let {
            Image(bitmap = it, contentDescription = "Share QR Code")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Or copy public ID", style = MaterialTheme.typography.bodyMedium)

        if (publicKey.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = publicKey, onValueChange = {},
                    enabled = false, singleLine = true,
                    modifier = Modifier
                        .weight(1f).padding(8.dp)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { copyToClipboard(context, publicKey) }) {
                    Icon(painter = painterResource(id = R.drawable.baseline_content_copy_24),
                        contentDescription = "Copy Public Key")
                }
            }
        }
    }
}

@Composable
fun CustomToolbar(title: String, onShareClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.weight(0.2f))
        Text(text = title, style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = onShareClick) {
            Icon(imageVector = Icons.Default.Share, contentDescription = "Share Public Key")
        }
    }
}

fun sharePublicKey(context: Context, publicKey: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, publicKey)
    }
    context.startActivity(Intent.createChooser(intent, "Share Public Key"))
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Public Key", text))
}