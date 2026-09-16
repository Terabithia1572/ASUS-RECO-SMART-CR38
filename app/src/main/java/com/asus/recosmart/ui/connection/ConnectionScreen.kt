package com.asus.recosmart.ui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val sessionState by viewModel.sessionState.collectAsState()
    val cameraStatus by viewModel.cameraStatus.collectAsState()
    val isMockMode by viewModel.isMockMode.collectAsState()
    val cameraIp by viewModel.cameraIp.collectAsState()
    val commandPort by viewModel.commandPort.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ASUS RECO Smart",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CR38 Kamera Kontrolü",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = PrimaryCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Field Test RC4",
                            color = PrimaryCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

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
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (isMockMode) Color(0xFFF97316) else SuccessGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMockMode) "SİMÜLASYON" else "GERÇEK KAMERA",
                        color = if (isMockMode) Color(0xFFF97316) else SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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

        // Status Card
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
                    text = "Oturum Bilgileri",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = PrimaryCyan
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Durum:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = when (sessionState) {
                            is SessionState.Connected -> if (isMockMode) "Simülasyon Aktif" else "Kamera Bağlandı"
                            is SessionState.Connecting, is SessionState.SessionStarting -> "Oturum Başlatılıyor"
                            is SessionState.TcpConnected -> "TCP Bağlandı"
                            is SessionState.Disconnected -> "Bağlantı Yok"
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
                    Text("Aktif Token:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = when (val state = sessionState) {
                            is SessionState.Connected -> "${state.token}"
                            else -> "${cameraStatus.activeToken}"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kamera Modeli:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${cameraStatus.brand} ${cameraStatus.model}",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (cameraStatus.firmwareVersion.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Yazılım Versiyonu (FW):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = cameraStatus.firmwareVersion,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Canlı RTSP Akışı:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = cameraStatus.rtspUrl, fontSize = 12.sp, color = PrimaryCyan)
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
