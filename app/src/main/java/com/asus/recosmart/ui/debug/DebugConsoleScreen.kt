package com.asus.recosmart.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asus.recosmart.ui.theme.DarkBackground
import com.asus.recosmart.ui.theme.PrimaryCyan
import com.asus.recosmart.ui.theme.RecordRed

@Composable
fun DebugConsoleScreen(
    viewModel: DebugConsoleViewModel = viewModel()
) {
    val debugLogs by viewModel.debugLogs.collectAsState()
    val cameraStatus by viewModel.cameraStatus.collectAsState()
    val customMsgId by viewModel.customMsgId.collectAsState()
    val customParam by viewModel.customParam.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current

        // Responsive 3-Row Header Layout
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: Title & Icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = PrimaryCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Protokol Hata Ayıklama Konsolu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Row 2: Subtitle Metadata
            Text(
                text = "TCP Port 7878 JSON Frames | Token: ${cameraStatus.activeToken}",
                style = MaterialTheme.typography.labelSmall,
                color = PrimaryCyan
            )

            // Row 3: Scrollable Action Buttons
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Button(
                        onClick = { viewModel.runHardwareDiagnostic() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.asus.recosmart.ui.theme.SuccessGreen)
                    ) {
                        Text("Donanım Tanılama", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }
                }
                item {
                    Button(
                        onClick = { viewModel.runSelfTest() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "Mock Self-Test",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCyan,
                            maxLines = 1
                        )
                    }
                }
                item {
                    IconButton(onClick = { viewModel.copyLogsToClipboard(context) }) {
                        Icon(Icons.Default.Share, contentDescription = "Logları Paylaş", tint = PrimaryCyan)
                    }
                }
                item {
                    IconButton(onClick = { viewModel.clearLogs() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Logları Temizle", tint = RecordRed)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Command Chips
        val quickCommands = androidx.compose.runtime.remember {
            listOf(
                "START_SESSION" to ("257" to ""),
                "GET_SETTINGS" to ("3" to ""),
                "APP_STATUS" to ("1" to "app_status"),
                "RESET_TO_VF" to ("259" to "force"),
                "STOP_VF" to ("260" to ""),
                "TAKE_PHOTO" to ("769" to ""),
                "REC_START" to ("513" to ""),
                "REC_STOP" to ("514" to ""),
                "LS" to ("1282" to "/tmp/fuse_d/DCIM/"),
                "GET_DEV_INFO" to ("11" to "")
            )
        }

        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(quickCommands.size) { idx ->
                val cmdItem = quickCommands[idx]
                val label = cmdItem.first
                val msgId = cmdItem.second.first
                val param = cmdItem.second.second
                AssistChip(
                    onClick = { viewModel.selectQuickCommand(msgId, param) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Raw Command Sender Terminal Bar
        val isDestructiveMsgId = androidx.compose.runtime.remember(customMsgId) {
            val id = customMsgId.toIntOrNull()
            id in setOf(4, 1281, 53258, 53272, 1286, 53275)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customMsgId,
                        onValueChange = { viewModel.onMsgIdChanged(it) },
                        label = { Text("msg_id") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = customParam,
                        onValueChange = { viewModel.onParamChanged(it) },
                        label = { Text("param (opt)") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )

                    IconButton(
                        onClick = { viewModel.sendCustomCommand() },
                        modifier = Modifier.background(
                            if (isDestructiveMsgId) RecordRed else PrimaryCyan,
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }

                if (isDestructiveMsgId) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠️ CAUTION: Sensitive/Destructive command msg_id $customMsgId",
                        color = RecordRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live Log Output Terminal Window
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            if (debugLogs.isEmpty()) {
                Text(
                    text = "No protocol logs captured yet. Execute commands or connect to view raw JSON frames.",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(debugLogs.reversed()) { logEntry ->
                        val logColor = when {
                            logEntry.contains("TX ->") -> PrimaryCyan
                            logEntry.contains("RX <-") -> Color(0xFF10B981)
                            logEntry.contains("ERROR") -> RecordRed
                            else -> Color.LightGray
                        }
                        Text(
                            text = logEntry,
                            color = logColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
