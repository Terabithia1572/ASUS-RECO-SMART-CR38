package com.asus.recosmart.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
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
import com.asus.recosmart.ui.theme.SuccessGreen

@Composable
fun FileManagerScreen(
    viewModel: FileManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsState()
    val rawFilesCount by viewModel.rawFilesCount.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val storageFilter by viewModel.storageFilter.collectAsState()
    val folderFilter by viewModel.folderFilter.collectAsState()
    val availableFolders by viewModel.availableFolders.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val exportProgressMap by viewModel.exportProgressMap.collectAsState()

    var fileToDelete by remember { mutableStateOf<CameraFile?>(null) }
    var fileToPlay by remember { mutableStateOf<CameraFile?>(null) }
    var fileToViewPhoto by remember { mutableStateOf<CameraFile?>(null) }
    var showSortDropdown by remember { mutableStateOf(false) }

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Sort Dropdown Button
                Box {
                    OutlinedButton(
                        onClick = { showSortDropdown = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan)
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = sortMode.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }

                    DropdownMenu(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false }
                    ) {
                        SortMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.label,
                                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sortMode == mode) PrimaryCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.setSortMode(mode)
                                    showSortDropdown = false
                                }
                            )
                        }
                    }
                }

                IconButton(onClick = { viewModel.loadFiles() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = PrimaryCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Kayıtlarda ara (örn: FILE4089, EMRG, 116MEDIA)...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryCyan) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Temizle", tint = Color.Gray)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(FileFilterCategory.values()) { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setCategory(category) },
                    label = { Text(category.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryCyan,
                        selectedLabelColor = DarkBackground,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Storage & Folder Filter Row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Storage Filters
            items(StorageFilter.values()) { filter ->
                val isSelected = storageFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setStorageFilter(filter) },
                    label = { Text(filter.label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SuccessGreen.copy(alpha = 0.3f),
                        selectedLabelColor = SuccessGreen,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // Dynamic Folder Filters
            if (availableFolders.isNotEmpty()) {
                item {
                    val isAllFolder = folderFilter == "ALL"
                    FilterChip(
                        selected = isAllFolder,
                        onClick = { viewModel.setFolderFilter("ALL") },
                        label = { Text("Tüm Klasörler", fontSize = 11.sp) }
                    )
                }
                items(availableFolders) { folder ->
                    val isSelected = folderFilter == folder
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFolderFilter(folder) },
                        label = { Text(folder, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryCyan.copy(alpha = 0.25f),
                            selectedLabelColor = PrimaryCyan,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Result Summary & Status
        if (!statusMessage.isNullOrEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusMessage!!,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryCyan
                )
            }
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
                Text("Görüntülenecek kayıt bulunamadı.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(files, key = { it.filename }) { file ->
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
    val isDownloadedOnPhone = exportProgress?.savedUri != null

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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (file.isEmergency) RecordRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))

                            // Folder Badge (Single-line guarantee)
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
                            Spacer(modifier = Modifier.width(6.dp))

                            // Single-line Status Badges ("Telefonda" / "Kamerada")
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isDownloadedOnPhone) SuccessGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isDownloadedOnPhone) "Telefonda" else "Kamerada",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isDownloadedOnPhone) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (file.dateTime.isNotEmpty()) {
                            Text(
                                text = file.dateTime,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
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
