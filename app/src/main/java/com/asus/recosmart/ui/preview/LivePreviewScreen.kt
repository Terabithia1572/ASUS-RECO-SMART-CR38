package com.asus.recosmart.ui.preview

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.asus.recosmart.domain.model.CameraStatus
import com.asus.recosmart.domain.model.SessionState
import com.asus.recosmart.ui.theme.DarkBackground
import com.asus.recosmart.ui.theme.PrimaryCyan
import com.asus.recosmart.ui.theme.RecordRed
import com.asus.recosmart.ui.theme.SurfaceDark

@Composable
fun LivePreviewScreen(
    viewModel: LivePreviewViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraStatus by viewModel.cameraStatus.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val isMockMode by viewModel.isMockMode.collectAsState()
    val statusText by viewModel.statusText.collectAsState()

    val isRealConnected = !isMockMode && sessionState is SessionState.Connected

    var rtspStreamState by remember { mutableStateOf("Connecting to RTSP...") }
    var isPlayerError by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var showStopRecordingDialog by remember { mutableStateOf(false) }

    // RTSP ExoPlayer Lifecycle handling
    DisposableEffect(isRealConnected) {
        var localPlayer: ExoPlayer? = null
        var isFallbackAttempted = false

        if (isRealConnected) {
            com.asus.recosmart.data.network.CameraNetworkManager.bindProcessToWifi(context)
            rtspStreamState = "Preparing Viewfinder (RESET_TO_VF)..."
            isPlayerError = false

            val requestTimestamp = System.currentTimeMillis()
            viewModel.logRtsp("[TIMING] LIVE_VIEW_REQUESTED at $requestTimestamp")

            fun createAndStartPlayer(forceTcp: Boolean) {
                val playerInitTime = System.currentTimeMillis()
                viewModel.logRtsp("[RTSP PLAYER] Initializing player (URL: ${CameraStatus.DEFAULT_RTSP_URL}, ForceRtpTcp: $forceTcp)...")
                val mediaItem = MediaItem.fromUri(CameraStatus.DEFAULT_RTSP_URL)
                val rtspMediaSource = RtspMediaSource.Factory()
                    .setForceUseRtpTcp(forceTcp)
                    .createMediaSource(mediaItem)

                val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(250, 500, 100, 250)
                    .build()

                val player = ExoPlayer.Builder(context)
                    .setLoadControl(loadControl)
                    .build().apply {
                    setMediaSource(rtspMediaSource)
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> rtspStreamState = "Buffering..."
                                Player.STATE_READY -> rtspStreamState = if (isPlaying) "Live" else "Ready (paused)"
                                Player.STATE_ENDED -> rtspStreamState = "Stream ended"
                                Player.STATE_IDLE -> rtspStreamState = "Idle"
                            }
                        }

                        override fun onRenderedFirstFrame() {
                            rtspStreamState = "Live"
                            viewModel.setFirstVideoFrameRendered(true)
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            val errDetail = error.localizedMessage ?: error.errorCodeName
                            if (!isFallbackAttempted) {
                                isFallbackAttempted = true
                                localPlayer?.release()
                                createAndStartPlayer(!forceTcp)
                            } else {
                                isPlayerError = true
                                rtspStreamState = "RTSP error: $errDetail"
                            }
                        }
                    })
                    prepare()
                    playWhenReady = true
                }
                localPlayer = player
                exoPlayer = player
            }

            viewModel.prepareLiveView { success, errorMsg ->
                if (!success) {
                    isPlayerError = true
                    rtspStreamState = errorMsg ?: "RESET_TO_VF failed"
                    return@prepareLiveView
                }
                rtspStreamState = "Connecting to RTSP..."
                createAndStartPlayer(forceTcp = false)
            }

            onDispose {
                viewModel.setFirstVideoFrameRendered(false)
                localPlayer?.stop()
                localPlayer?.release()
                exoPlayer = null
                viewModel.logRtsp("[LOCAL][origin=LIVE_SCREEN_EXIT] RTSP renderer released. Physical recording state preserved.")
            }
        } else {
            onDispose { }
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer?.pause()
                Lifecycle.Event.ON_RESUME -> if (isRealConnected && exoPlayer?.isPlaying == false) exoPlayer?.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "RecordPulse")
    val recordAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Alpha"
    )

    // Stop Recording Confirmation Dialog
    if (showStopRecordingDialog) {
        AlertDialog(
            onDismissRequest = { showStopRecordingDialog = false },
            title = { Text("Kaydı Durdur?", fontWeight = FontWeight.Bold) },
            text = { Text("Kamera şu anda kayıt yapıyor. Kaydı durdurmak istiyor musunuz?") },
            confirmButton = {
                Button(
                    onClick = {
                        showStopRecordingDialog = false
                        viewModel.toggleRecording()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RecordRed)
                ) {
                    Text("Kaydı Durdur", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStopRecordingDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Video Viewport Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                isMockMode -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = PrimaryCyan.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "SİMÜLASYON GÖRÜNTÜSÜ",
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = CameraStatus.DEFAULT_RTSP_URL,
                            color = PrimaryCyan,
                            fontSize = 12.sp
                        )
                    }
                }

                !isRealConnected -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Kamera bağlı değil",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Lütfen önce Wi-Fi ağından CR38 kameraya bağlanın ve Bağlantı sekmesinden oturum açın.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    exoPlayer?.let { player ->
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    this.player = player
                                }
                            },
                            update = { playerView ->
                                playerView.player = player
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (isPlayerError || rtspStreamState != "Live") {
                        val displayState = when (rtspStreamState) {
                            "Preparing Viewfinder (RESET_TO_VF)..." -> "Viewfinder hazırlanıyor (RESET_TO_VF)..."
                            "Connecting to RTSP..." -> "Canlı görüntü bağlanıyor..."
                            "Buffering..." -> "Hazırlanıyor..."
                            "Live" -> "Canlı Görüntü Aktif"
                            "Paused" -> "Duraklatıldı"
                            else -> if (isPlayerError) "Canlı görüntü alınamadı" else rtspStreamState
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = displayState,
                                color = if (isPlayerError) RecordRed else PrimaryCyan,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Overlay Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cameraStatus.isRecording) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(RecordRed.copy(alpha = recordAlpha))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "KAYIT",
                            color = RecordRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Green)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "HAZIR",
                            color = Color.Green,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                val batDisplay = if (isMockMode) "${cameraStatus.batteryLevel}%" else if (cameraStatus.batteryLevel >= 0) "${cameraStatus.batteryLevel}%" else "--"
                val sdDisplay = if (isMockMode) (if (cameraStatus.sdCardStatus == com.asus.recosmart.domain.model.SdCardStatus.READY) "HAZIR" else "${cameraStatus.sdCardStatus}") else if (cameraStatus.sdCardStatus != com.asus.recosmart.domain.model.SdCardStatus.ERROR) "${cameraStatus.sdCardStatus}" else "--"

                Text(
                    text = "BAT: $batDisplay | SD: $sdDisplay",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hardware Emergency Recording Info Banner
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                Text(
                    text = "Acil Kayıt — Donanım desteği araştırılıyor",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!statusText.isNullOrEmpty()) {
            Text(
                text = statusText!!,
                color = PrimaryCyan,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Camera Controls Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shutter Photo Button
            FloatingActionButton(
                onClick = { viewModel.takePhoto() },
                containerColor = SurfaceDark,
                contentColor = PrimaryCyan,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Fotoğraf Çek",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Record Start / Stop Button with Confirmation Dialog
            FloatingActionButton(
                onClick = {
                    if (cameraStatus.isRecording) {
                        showStopRecordingDialog = true
                    } else {
                        viewModel.toggleRecording()
                    }
                },
                containerColor = if (cameraStatus.isRecording) RecordRed else PrimaryCyan,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (cameraStatus.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                    contentDescription = if (cameraStatus.isRecording) "Kaydı Durdur" else "Kaydı Başlat",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
