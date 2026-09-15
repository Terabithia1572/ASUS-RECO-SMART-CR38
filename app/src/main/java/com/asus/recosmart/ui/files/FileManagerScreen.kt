package com.asus.recosmart.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val files by viewModel.files.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var fileToDelete by remember { mutableStateOf<CameraFile?>(null) }

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
                    Text("Kayıtlar yükleniyor...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
                    FileRowItem(
                        file = file,
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
                text = { Text("Bu işlem geri alınamaz.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
    }
}

@Composable
fun FileRowItem(
    file: CameraFile,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                        file.isVideo -> Icons.Default.Movie
                        else -> Icons.Default.Image
                    },
                    contentDescription = null,
                    tint = if (file.isVideo) PrimaryCyan else Color(0xFFF6AD55),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = file.filename,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val typeLabel = if (file.isVideo) "Video" else "Fotoğraf"
                    Text(
                        text = "$typeLabel • ${file.formattedSize}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (file.dateTime.isNotEmpty()) {
                        Text(
                            text = file.dateTime,
                            fontSize = 11.sp,
                            color = PrimaryCyan.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            IconButton(onClick = onDeleteClick) {
                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = RecordRed)
            }
        }
    }
}
