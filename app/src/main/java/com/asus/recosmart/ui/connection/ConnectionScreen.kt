package com.asus.recosmart.ui.connection

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asus.recosmart.domain.model.SessionState
import com.asus.recosmart.ui.theme.DarkBackground
import com.asus.recosmart.ui.theme.PrimaryCyan
import com.asus.recosmart.ui.theme.RecordRed
import com.asus.recosmart.ui.theme.SuccessGreen

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel = viewModel()
) {
    val context = LocalContext.current
    val sessionState by viewModel.sessionState.collectAsState()
    val cameraStatus by viewModel.cameraStatus.collectAsState()
    val isMockMode by viewModel.isMockMode.collectAsState()
    val cameraIp by viewModel.cameraIp.collectAsState()
    val commandPort by viewModel.commandPort.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val isVehicleModeEnabled by viewModel.isVehicleModeEnabled.collectAsState()
    val autoStartRecordingIfIdle by viewModel.autoStartRecordingIfIdle.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "ASUS RECO Smart",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CR38 Kontrolü",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = PrimaryCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Field Test RC7",
                                color = PrimaryCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Mode Badge
                Surface(
                    color = if (isMockMode) Color(0xFFF97316).copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isMockMode) Color(0xFFF97316) else SuccessGreen
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (isMockMode) Color(0xFFF97316) else SuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isMockMode) "SİMÜLASYON" else "GERÇEK KAMERA",
                            color = if (isMockMode) Color(0xFFF97316) else SuccessGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // Wi-Fi Connection Helper (Visible when disconnected and in Real Camera mode)
        if (!isMockMode && !sessionState.isConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PrimaryCyan.copy(alpha = 0.10f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(32.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wi-Fi Bağlantı Yardımcısı",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = PrimaryCyan
                        )
                        Text(
                            text = "Telefonunuzu kameranın Wi-Fi ağına (CR38_...) bağlayın.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan)
                    ) {
                        Text("Wi-Fi Ayarları", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Connection Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Kamera Bağlantısı",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = PrimaryCyan
                )

                // Mode Selector Segmented Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isMockMode,
                        onClick = { viewModel.toggleMockMode(false) },
                        label = { Text("Gerçek Kamera", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryCyan.copy(alpha = 0.2f),
                            selectedLabelColor = PrimaryCyan
                        )
                    )
                    FilterChip(
                        selected = isMockMode,
                        onClick = { viewModel.toggleMockMode(true) },
                        label = { Text("Simülasyon", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFF97316).copy(alpha = 0.2f),
                            selectedLabelColor = Color(0xFFF97316)
                        )
                    )
                }

                // Mode Hint Text
                Text(
                    text = if (isMockMode)
                        "Fiziksel kameraya bağlantı kurulmaz. Uygulama test verileriyle çalışır."
                    else
                        "Önce telefonunuzu CR38 kameranın Wi-Fi ağına (CR38_...) bağlayın.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = cameraIp,
                    onValueChange = { viewModel.onIpChanged(it) },
                    label = { Text("Kamera IP Adresi") },
                    leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isMockMode
                )

                OutlinedTextField(
                    value = commandPort,
                    onValueChange = { viewModel.onPortChanged(it) },
                    label = { Text("Komut TCP Portu") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isMockMode
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (sessionState.isConnected) {
                    Button(
                        onClick = { viewModel.disconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Bağlantıyı Kes", fontWeight = FontWeight.Bold)
                    }
                } else {
                    val isPending = sessionState is SessionState.TcpConnected || sessionState is SessionState.SessionStarting
                    Button(
                        onClick = { viewModel.connect() },
                        enabled = !isPending,
                        colors = ButtonDefaults.buttonColors(containerColor = if (isMockMode) Color(0xFFF97316) else PrimaryCyan),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isPending) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (sessionState is SessionState.TcpConnected) "TCP Bağlandı..." else "Oturum Başlatılıyor...")
                        } else {
                            Text(if (isMockMode) "Simülasyonu Başlat" else "Kameraya Bağlan", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Vehicle Dashboard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = PrimaryCyan)
                    Text(
                        text = "Araç Gösterge Paneli",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryCyan
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Araç Modu Otomasyonu", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Kameraya bağlanıldığında durumu otomatik kontrol et", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isVehicleModeEnabled,
                        onCheckedChange = { viewModel.setVehicleMode(it) }
                    )
                }

                if (isVehicleModeEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Boştaysa Otomatik Kayıt", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Kamera kayıt yapmıyorsa bağlandığında otomatik kayıt başlat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoStartRecordingIfIdle,
                            onCheckedChange = { viewModel.setAutoStartRecording(it) }
                        )
                    }
                }
            }
        }

        // Status & Facts Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Donanım & Oturum Bilgileri",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryCyan
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bağlantı:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = when (sessionState) {
                            is SessionState.Connected -> if (isMockMode) "Simülasyon Aktif" else "Bağlı"
                            is SessionState.Connecting, is SessionState.SessionStarting -> "Oturum Başlatılıyor"
                            is SessionState.TcpConnected -> "TCP Bağlandı"
                            is SessionState.Disconnected -> "Bağlı Değil"
                            is SessionState.Error -> "Bağlantı Hatası"
                        },
                        fontWeight = FontWeight.Bold,
                        color = when (sessionState) {
                            is SessionState.Connected -> if (isMockMode) Color(0xFFF97316) else SuccessGreen
                            is SessionState.Connecting, is SessionState.TcpConnected, is SessionState.SessionStarting -> PrimaryCyan
                            else -> RecordRed
                        }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kayıt Durumu:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (cameraStatus.isRecording) "Kayıt Yapıyor" else "Hazır",
                        fontWeight = FontWeight.Bold,
                        color = if (cameraStatus.isRecording) RecordRed else SuccessGreen
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aktif Token:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = when (val state = sessionState) {
                            is SessionState.Connected -> "${state.token}"
                            else -> if (cameraStatus.activeToken > 0) "${cameraStatus.activeToken}" else "Yok"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kamera Modeli:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "SanJet DR38AS (ASUS RECO Smart)",
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kamera IP:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = cameraIp, fontWeight = FontWeight.Bold)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Canlı RTSP Akışı:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (sessionState.isConnected) cameraStatus.rtspUrl else "Kapalı",
                        fontSize = 12.sp,
                        color = if (sessionState.isConnected) PrimaryCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val derivedStatusText = when (val state = sessionState) {
                    is SessionState.Connected -> if (isMockMode) "Simülasyon bağlantısı aktif." else "Kamera bağlantısı başarılı."
                    is SessionState.TcpConnected -> "TCP soket bağlantısı kuruldu (Port 7878). Oturum başlatılıyor..."
                    is SessionState.Connecting, is SessionState.SessionStarting -> "START_SESSION (msg_id 257) gönderiliyor, token bekleniyor..."
                    is SessionState.Error -> "Bağlantı hatası: ${state.message}"
                    is SessionState.Disconnected -> if (statusMessage.isNullOrEmpty()) "Bağlantı bekleniyor." else statusMessage
                }

                val derivedStatusColor = when (sessionState) {
                    is SessionState.Connected -> if (isMockMode) Color(0xFFF97316) else SuccessGreen
                    is SessionState.Connecting, is SessionState.TcpConnected, is SessionState.SessionStarting -> PrimaryCyan
                    is SessionState.Error -> RecordRed
                    is SessionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                if (!derivedStatusText.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = derivedStatusText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = derivedStatusColor
                    )
                }
            }
        }
    }
}
