package com.asus.recosmart.ui.files

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.asus.recosmart.data.network.CameraNetworkManager
import com.asus.recosmart.data.network.MediaDownloader
import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.ui.theme.DarkBackground
import com.asus.recosmart.ui.theme.PrimaryCyan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun InternalMediaPlayerDialog(
    file: CameraFile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Bind process to Wi-Fi to ensure clear-text HTTP media requests target the camera
    LaunchedEffect(Unit) {
        CameraNetworkManager.bindProcessToWifi(context)
    }

    val exoPlayer = remember(file.httpUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(file.httpUrl))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var actionStatusText by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableIntStateOf(-1) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Video Oynatıcı",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCyan
                        )
                        Text(
                            text = "${file.filename} • ${file.folder}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ExoPlayer Surface
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color.Black, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Custom Playback Controls
                Slider(
                    value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                    onValueChange = { percent ->
                        val targetMs = (percent * durationMs).toLong()
                        currentPositionMs = targetMs
                        exoPlayer.seekTo(targetMs)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryCyan,
                        activeTrackColor = PrimaryCyan,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimeMs(currentPositionMs),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                            tint = PrimaryCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = formatTimeMs(durationMs),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!actionStatusText.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = actionStatusText!!,
                        fontSize = 12.sp,
                        color = PrimaryCyan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Export Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                downloadProgress = 0
                                actionStatusText = "Telefona indiriliyor (%0)..."
                                val res = MediaDownloader.downloadToMediaStore(context, file) { pct ->
                                    downloadProgress = pct
                                    actionStatusText = "Telefona indiriliyor (%$pct)..."
                                }
                                if (res.isSuccess) {
                                    actionStatusText = "Video telefona kaydedildi!"
                                } else {
                                    actionStatusText = "İndirme başarısız: ${res.exceptionOrNull()?.localizedMessage}"
                                }
                                downloadProgress = -1
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = downloadProgress < 0,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kaydet", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                actionStatusText = "Dış uygulama için hazırlanıyor..."
                                val cacheRes = MediaDownloader.getOrCacheFile(context, file)
                                if (cacheRes.isSuccess) {
                                    actionStatusText = null
                                    MediaDownloader.openInExternalApp(context, cacheRes.getOrThrow(), isVideo = true)
                                } else {
                                    actionStatusText = "Hazırlanamadı: ${cacheRes.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aç", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                actionStatusText = "Paylaşım için hazırlanıyor..."
                                val cacheRes = MediaDownloader.getOrCacheFile(context, file)
                                if (cacheRes.isSuccess) {
                                    actionStatusText = null
                                    MediaDownloader.shareMedia(context, cacheRes.getOrThrow(), isVideo = true)
                                } else {
                                    actionStatusText = "Hazırlanamadı: ${cacheRes.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paylaş", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(millis: Long): String {
    val totalSec = millis / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
