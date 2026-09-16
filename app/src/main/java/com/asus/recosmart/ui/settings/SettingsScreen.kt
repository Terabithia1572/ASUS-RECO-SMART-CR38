package com.asus.recosmart.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asus.recosmart.domain.model.CameraSettingSpec
import com.asus.recosmart.domain.model.SessionState
import com.asus.recosmart.ui.theme.DarkBackground
import com.asus.recosmart.ui.theme.PrimaryCyan
import com.asus.recosmart.ui.theme.RecordRed

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val settingSpecs by viewModel.settingSpecs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val sessionState by viewModel.sessionState.collectAsState()
    val isMockMode by viewModel.isMockMode.collectAsState()

    val isSettingMutationInProgress by viewModel.isSettingMutationInProgress.collectAsState()

    val canEdit = (isMockMode || sessionState is SessionState.Connected) && !isSettingMutationInProgress

    var selectedSpecForEdit by remember { mutableStateOf<CameraSettingSpec?>(null) }
    var showFormatConfirmDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Kamera Ayarları",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (isMockMode) "ASUS CR38 • SİMÜLASYON" else "ASUS CR38 • GERÇEK KAMERA",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryCyan
                )
            }
            Row {
                IconButton(onClick = { viewModel.loadSettings() }, enabled = canEdit) {
                    Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = if (canEdit) PrimaryCyan else Color.Gray)
                }
                IconButton(onClick = { viewModel.resetToVf() }, enabled = canEdit) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Kamerayı Sıfırla", tint = if (canEdit) RecordRed else Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!canEdit && !isSettingMutationInProgress) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RecordRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kamera bağlı değil. Ayarları değiştirmek için Bağlantı sekmesinden bağlanın.",
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isSettingMutationInProgress) {
            Surface(
                color = PrimaryCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = PrimaryCyan,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = statusText ?: "Ayar uygulanıyor...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryCyan
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else if (!statusText.isNullOrEmpty()) {
            Text(
                text = statusText!!,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = PrimaryCyan
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryCyan)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(settingSpecs) { spec ->
                    SettingSpecCard(
                        spec = spec,
                        canEdit = canEdit,
                        onClick = {
                            if (canEdit && spec.isEditable && spec.options.isNotEmpty()) {
                                selectedSpecForEdit = spec
                            }
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Maintenance & Safety Danger Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Bakım ve Güvenlik İşlemleri",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = RecordRed
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { showFormatConfirmDialog = true },
                                    enabled = canEdit,
                                    colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SD Kartı Biçimlendir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { showResetConfirmDialog = true },
                                    enabled = canEdit,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, tint = RecordRed)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Fabrika Ayarlarına Dön", fontSize = 11.sp, color = RecordRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Modal Option Selector Dialog
    selectedSpecForEdit?.let { spec ->
        AlertDialog(
            onDismissRequest = { selectedSpecForEdit = null },
            title = {
                Text(
                    text = spec.label,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryCyan
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = spec.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    spec.options.forEach { option ->
                        val isSelected = option.wireValue == spec.currentWireValue
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSetting(spec.key, option.wireValue)
                                    selectedSpecForEdit = null
                                },
                            color = if (isSelected) PrimaryCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option.displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = if (isSelected) PrimaryCyan else MaterialTheme.colorScheme.onSurface
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateSetting(spec.key, option.wireValue)
                                        selectedSpecForEdit = null
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedSpecForEdit = null }) {
                    Text("İptal", color = Color.Gray)
                }
            }
        )
    }

    // Format SD Confirmation Dialog
    if (showFormatConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFormatConfirmDialog = false },
            title = {
                Text("SD Kart Biçimlendirilsin mi?", fontWeight = FontWeight.Bold, color = RecordRed)
            },
            text = {
                Text("Kameradaki tüm fotoğraf ve videolar silinecektir. Bu işlem geri alınamaz.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFormatConfirmDialog = false
                        viewModel.formatSdCard()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RecordRed)
                ) {
                    Text("SD Kartı Biçimlendir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFormatConfirmDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // Factory Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text("Fabrika Ayarlarına Dönülsün mü?", fontWeight = FontWeight.Bold, color = RecordRed)
            },
            text = {
                Text("Kameranın yapılandırması varsayılan ayarlara döndürülecektir.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        viewModel.factoryReset()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RecordRed)
                ) {
                    Text("Sıfırla", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}

@Composable
fun SettingSpecCard(
    spec: CameraSettingSpec,
    canEdit: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canEdit && spec.isEditable && spec.options.isNotEmpty()) { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = spec.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!spec.isEditable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color.DarkGray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Salt okunur",
                                fontSize = 10.sp,
                                color = Color.LightGray,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = spec.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                color = if (spec.isEditable && canEdit) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = spec.currentDisplayValue,
                    color = if (spec.isEditable && canEdit) PrimaryCyan else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}
