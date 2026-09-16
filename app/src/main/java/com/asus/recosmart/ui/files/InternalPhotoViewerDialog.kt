package com.asus.recosmart.ui.files

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.asus.recosmart.data.network.CameraNetworkManager
import com.asus.recosmart.data.network.MediaDownloader
import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.ui.theme.DarkBackground
import com.asus.recosmart.ui.theme.PrimaryCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@Composable
fun InternalPhotoViewerDialog(
    file: CameraFile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var bitmapState by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var actionStatusText by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableIntStateOf(-1) }
    var extractedDimensions by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.httpUrl) {
        CameraNetworkManager.bindProcessToWifi(context)
        withContext(Dispatchers.IO) {
            try {
                // Step 1: Decode bounds first without full memory allocation (inJustDecodeBounds)
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                val connBounds = URL(file.httpUrl).openConnection() as HttpURLConnection
                connBounds.connectTimeout = 5000
                connBounds.readTimeout = 5000
                connBounds.doInput = true
                connBounds.connect()
                val boundsStream: InputStream = connBounds.inputStream
                BitmapFactory.decodeStream(boundsStream, null, boundsOptions)
                boundsStream.close()
                connBounds.disconnect()

                val width = boundsOptions.outWidth
                val height = boundsOptions.outHeight
                if (width > 0 && height > 0) {
                    val mp = (width.toLong() * height.toLong()) / 1_000_000.0
                    extractedDimensions = "Gerçek dosya çözünürlüğü: ${width} × ${height} (~${String.format(Locale.US, "%.1f", mp)} MP)"
                }

                // Step 2: Download stream for full bitmap display
                val conn = URL(file.httpUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.doInput = true
                conn.connect()
                val input = conn.inputStream
                val bitmap = BitmapFactory.decodeStream(input)
                input.close()
                conn.disconnect()

                if (bitmap != null) {
                    bitmapState = bitmap
                    if (extractedDimensions == null) {
                        val mp = (bitmap.width.toLong() * bitmap.height.toLong()) / 1_000_000.0
                        extractedDimensions = "Gerçek dosya çözünürlüğü: ${bitmap.width} × ${bitmap.height} (~${String.format(Locale.US, "%.1f", mp)} MP)"
                    }
                } else {
                    errorMessage = "Fotoğraf çözümlenemedi."
                }
            } catch (e: Exception) {
                errorMessage = "Fotoğraf yüklenemedi: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
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
                            text = "Fotoğraf Görüntüleyici",
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

                Spacer(modifier = Modifier.height(8.dp))

                // Photo Resolution Truthfulness Info Banner
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                        Column {
                            Text(
                                text = extractedDimensions ?: "Görsel meta verisi bekleniyor...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryCyan
                            )
                            Text(
                                text = "CR38 kamerası wire ayarlarını kabul etse bile çıktı dosyasını donanım piksel çözünürlüğünde oluşturur.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Photo Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = PrimaryCyan)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Fotoğraf indiriliyor...", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        errorMessage != null -> {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }
                        bitmapState != null -> {
                            Image(
                                bitmap = bitmapState!!.asImageBitmap(),
                                contentDescription = file.filename,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }

                if (!actionStatusText.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = actionStatusText!!,
                        fontSize = 12.sp,
                        color = PrimaryCyan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Single-line Responsive Export Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                downloadProgress = 0
                                actionStatusText = "Fotoğraf telefona indiriliyor (%0)..."
                                val res = MediaDownloader.downloadToMediaStore(context, file) { pct ->
                                    downloadProgress = pct
                                    actionStatusText = "Fotoğraf telefona indiriliyor (%$pct)..."
                                }
                                if (res.isSuccess) {
                                    actionStatusText = "Fotoğraf telefona kaydedildi!"
                                } else {
                                    actionStatusText = "İndirme başarısız: ${res.exceptionOrNull()?.localizedMessage}"
                                }
                                downloadProgress = -1
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = downloadProgress < 0,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Kaydet",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                actionStatusText = "Dış uygulama için hazırlanıyor..."
                                val cacheRes = MediaDownloader.getOrCacheFile(context, file)
                                if (cacheRes.isSuccess) {
                                    actionStatusText = null
                                    MediaDownloader.openInExternalApp(context, cacheRes.getOrThrow(), isVideo = false)
                                } else {
                                    actionStatusText = "Hazırlanamadı: ${cacheRes.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Aç",
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                actionStatusText = "Paylaşım için hazırlanıyor..."
                                val cacheRes = MediaDownloader.getOrCacheFile(context, file)
                                if (cacheRes.isSuccess) {
                                    actionStatusText = null
                                    MediaDownloader.shareMedia(context, cacheRes.getOrThrow(), isVideo = false)
                                } else {
                                    actionStatusText = "Hazırlanamadı: ${cacheRes.exceptionOrNull()?.localizedMessage}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "Paylaş",
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
