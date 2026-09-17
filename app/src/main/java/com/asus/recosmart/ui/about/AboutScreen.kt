package com.asus.recosmart.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asus.recosmart.ui.theme.DarkBackground
import com.asus.recosmart.ui.theme.PrimaryCyan

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
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
                    text = "Hakkında",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ASUS RECO Smart CR38 Kontrolü",
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryCyan
                )
            }

            Surface(
                color = PrimaryCyan.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan)
            ) {
                Text(
                    text = "Field Test RC7.5",
                    color = PrimaryCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // App Main Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Proje Hakkında",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryCyan
                    )
                }

                Text(
                    text = "ASUS RECO Smart, ASUS RECO Smart CR38 / SanJet DR38AS araç kameralarının modern Android cihazlarda yönetilebilmesi amacıyla geliştirilmiş bağımsız bir Android uygulamasıdır.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )

                Text(
                    text = "Uygulama; kamera bağlantısı, canlı görüntü, araç modu otomasyonu, video kaydı, fotoğraf çekimi, kamera kayıtlarının görüntülenmesi, cihazdan telefona medya aktarımı ve desteklenen kamera ayarlarının yönetilmesi gibi işlevler sunmaktadır.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Text(
                    text = "Bu proje, eski nesil kamera donanımının güncel Android sürüpleriyle yeniden kullanılabilmesini sağlamak amacıyla geliştirilmiştir.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // Developer & Copyright Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Geliştirici & Lisans",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = PrimaryCyan
                    )
                }
                Text(
                    text = "Yunus İNAN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Telif Hakkı",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "© 2026 Yunus İNAN. Tüm hakları saklıdır.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Technical Specs Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Teknik Donanım Bilgileri",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = PrimaryCyan
                    )
                }

                TechInfoRow("Hedef Cihaz:", "SanJet DR38AS (ASUS RECO Smart CR38)")
                TechInfoRow("Kamera IP:", "192.168.42.1")
                TechInfoRow("Komut TCP Portu:", "7878")
                TechInfoRow("Veri TCP Portu:", "8787")
                TechInfoRow("Canlı Yayın:", "RTSP (rtsp://192.168.42.1/live)")
                TechInfoRow("Medya Erişimi:", "HTTP Server")
                TechInfoRow("İstemci Mimarisi:", "Kotlin + Jetpack Compose (Clean Architecture)")
            }
        }

        // Legal Disclaimer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFFF6AD55), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Yasal Uyarı & Marka Beyanı",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFF6AD55)
                    )
                }

                Text(
                    text = "ASUS ve RECO Smart markaları ASUSTeK Computer Inc. tescilli markalarıdır. Bu uygulama bağımsız açık kaynaklı topluluk kontrol yazılımıdır.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun TechInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
