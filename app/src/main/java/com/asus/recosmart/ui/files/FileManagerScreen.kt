package com.asus.recosmart.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asus.recosmart.domain.model.CameraFile
import com.asus.recosmart.ui.theme.DarkBackground
import com.asus.recosmart.ui.theme.PrimaryCyan
import com.asus.recosmart.ui.theme.RecordRed

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val exportProgressMap by viewModel.exportProgressMap.collectAsState()

    var fileToDelete by remember { mutableStateOf<CameraFile?>(null) }
    var fileToPlay by remember { mutableStateOf<CameraFile?>(null) }
    var fileToViewPhoto by remember { mutableStateOf<CameraFile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Kamera Kayıtları",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Dizin: /tmp/fuse_d/DCIM/",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryCyan
                )
            }

            IconButton(onClick = { viewModel.loadFiles() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = PrimaryCyan)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FileFilterCategory.values().forEach { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setCategory(category) },
                    label = { Text(category.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCyan,
                        selectedLabelColor = DarkBackground,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!statusMessage.isNullOrEmpty()) {
            Text(
                text = statusMessage!!,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PrimaryCyan)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Kamera dizinleri taranıyor...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Kamerada gösterilecek kayıt bulunamadı.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(files) { file ->
                    val exportProgress = exportProgressMap[file.filename]
                    FileRowItem(
                        file = file,
                        exportProgress = exportProgress,
                        onPlayClick = {
                            if (file.isVideo) {
                                fileToPlay = file
                            } else if (file.isPhoto) {
                                fileToViewPhoto = file
                            }
                        },
                        onDownloadClick = {
                            viewModel.downloadToPhone(context, file)
                        },
                        onOpenExternalClick = {
                            viewModel.openInExternalApp(context, file)
                        },
                        onShareClick = {
                            viewModel.shareMedia(context, file)
                        },
                        onDeleteClick = { fileToDelete = file }
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        fileToDelete?.let { targetFile ->
            AlertDialog(
                onDismissRequest = { fileToDelete = null },
                title = { Text("${targetFile.filename} Silinsin mi?", fontWeight = FontWeight.Bold) },
                text = { Text("Bu kaydı kameradan silmek istediğinize emin misiniz?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteFile(targetFile)
                            fileToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RecordRed)
                    ) {
                        Text("Sil", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { fileToDelete = null }) {
                        Text("İptal")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // In-App Internal Video Player Dialog
        fileToPlay?.let { targetVideo ->
            InternalMediaPlayerDialog(
                file = targetVideo,
                onDismiss = { fileToPlay = null }
            )
        }

        // In-App Internal Photo Viewer Dialog
        fileToViewPhoto?.let { targetPhoto ->
            InternalPhotoViewerDialog(
                file = targetPhoto,
                onDismiss = { fileToViewPhoto = null }
            )
        }
    }
}

@Composable
fun FileRowItem(
    file: CameraFile,
    exportProgress: FileExportProgress?,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onOpenExternalClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when {
                            file.isDirectory -> Icons.Default.Folder
                            file.isEmergency -> Icons.Default.Warning
                            file.isVideo -> Icons.Default.Movie
                            else -> Icons.Default.Image
                        },
                        contentDescription = null,
                        tint = when {
                            file.isEmergency -> RecordRed
                            file.isVideo -> PrimaryCyan
                            file.isDirectory -> Color(0xFFECC94B)
                            else -> Color(0xFFF6AD55)
                        },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.filename,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${file.mediaTypeLabel} • ${file.formattedSize}",
                                fontSize = 12.sp,
                                color = if (file.isEmergency) RecordRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = PrimaryCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = file.folder,
                                    fontSize = 10.sp,
                                    color = PrimaryCyan,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            if (exportProgress?.savedUri != null) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "İndirildi",
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        if (file.dateTime.isNotEmpty()) {
                            Text(
                                text = file.dateTime,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPlayClick) {
                    Icon(
                        imageVector = if (file.isVideo) Icons.Default.PlayArrow else Icons.Default.Image,
                        contentDescription = if (file.isVideo) "İzle" else "Görüntüle",
                        tint = PrimaryCyan
                    )
                }
                IconButton(onClick = onDownloadClick, enabled = exportProgress?.isDownloading != true) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Kaydet",
                        tint = if (exportProgress?.isDownloading == true) Color.Gray else PrimaryCyan
                    )
                }
                IconButton(onClick = onOpenExternalClick) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Aç",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onShareClick) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Paylaş",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Sil",
                        tint = RecordRed
                    )
                }
            }

            if (exportProgress?.isDownloading == true) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { exportProgress.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = PrimaryCyan
                )
            }
        }
    }
}

