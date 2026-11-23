package com.cream.translator.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.cream.translator.CreamTranslatorApp
import com.cream.translator.core.monitoring.LogEntry
import com.cream.translator.core.monitoring.crash.DiagnosticExportHelper
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showViewerDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    val app = context.applicationContext as? CreamTranslatorApp
    val bufferSize = app?.circularLogBuffer?.size ?: 0
    val avgLatency = app?.pipelineTracer?.calculateAverageLatencyMs() ?: 0L
    val hasCrash = app?.crashDiagnosticCollector?.hasCrashReport() ?: false

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Giám sát & Nhật ký chẩn đoán (Diagnostics)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            val avgLatencyText = if (avgLatency > 0) "${avgLatency}ms" else "Chưa ghi nhận"
            val crashStatusText = if (hasCrash) "Phát hiện sự cố chưa xử lý" else "Hệ thống ổn định"

            Text(
                text = "• Bộ nhớ đệm log: $bufferSize bản ghi",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "• Độ trễ dịch trung bình: $avgLatencyText",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "• Báo cáo sự cố: $crashStatusText",
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasCrash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { showViewerDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Xem nhật ký")
                }

                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val diagnosticDir = app?.diagnosticDirectory ?: context.cacheDir
                                val helper = DiagnosticExportHelper(context, diagnosticDir, app?.circularLogBuffer)
                                val shareIntent = helper.createShareIntent()
                                val chooser = Intent.createChooser(shareIntent, "Chia sẻ báo cáo chẩn đoán")
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                val errorMsg = "Lỗi khi xuất chẩn đoán: ${e.message}"
                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Xuất chẩn đoán")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { showClearConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Xóa sạch toàn bộ nhật ký")
            }
        }
    }

    if (showViewerDialog && app != null) {
        LogViewerDialog(
            logEntries = app.circularLogBuffer.snapshot(),
            onDismiss = { showViewerDialog = false },
        )
    }

    if (showClearConfirmDialog && app != null) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Xác nhận xóa nhật ký") },
            text = {
                Text(
                    "Thao tác này sẽ xóa toàn bộ nhật ký đệm, tệp log xoay vòng trên đĩa và " +
                        "báo cáo sự cố. Bạn có chắc chắn muốn tiếp tục?",
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        scope.launch {
                            app.circularLogBuffer.clear()
                            app.fileLogSink.clearLogs()
                            app.crashDiagnosticCollector.clearCrashReport()
                            showClearConfirmDialog = false
                            val toastMsg = "Đã xóa toàn bộ nhật ký chẩn đoán"
                            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Text("Xóa tất cả")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Hủy")
                }
            },
        )
    }
}

@Composable
fun LogViewerDialog(
    logEntries: List<LogEntry>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var selectedPriorityFilter by remember { mutableIntStateOf(0) } // 0 = ALL, 4 = INFO, 5 = WARN, 6 = ERROR

    val filteredEntries =
        remember(logEntries, selectedPriorityFilter) {
            if (selectedPriorityFilter == 0) {
                logEntries.takeLast(150)
            } else {
                logEntries.filter { it.priority >= selectedPriorityFilter }.takeLast(150)
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier =
            Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
        title = {
            Column {
                Text("Nhật ký hệ thống gần nhất", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = selectedPriorityFilter == 0,
                        onClick = { selectedPriorityFilter = 0 },
                        label = { Text("Tất cả (${logEntries.size})") },
                    )
                    FilterChip(
                        selected = selectedPriorityFilter == 4,
                        onClick = { selectedPriorityFilter = 4 },
                        label = { Text("INFO+") },
                    )
                    FilterChip(
                        selected = selectedPriorityFilter == 5,
                        onClick = { selectedPriorityFilter = 5 },
                        label = { Text("WARN+") },
                    )
                    FilterChip(
                        selected = selectedPriorityFilter == 6,
                        onClick = { selectedPriorityFilter = 6 },
                        label = { Text("ERROR") },
                    )
                }
            }
        },
        text = {
            if (filteredEntries.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Không có bản ghi log nào phù hợp bộ lọc.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                            .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(filteredEntries) { entry ->
                        val textColor =
                            when (entry.priority) {
                                6, 7 -> Color(0xFFFF6B68) // Error: Red
                                5 -> Color(0xFFFFD166) // Warn: Amber
                                4 -> Color(0xFF06D6A0) // Info: Mint green
                                else -> Color(0xFFE0E0E0) // Debug/Verbose: Grey
                            }
                        Text(
                            text = entry.toFormattedString(),
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val fullText = filteredEntries.joinToString("\n") { it.toFormattedString() }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = ClipData.newPlainText("CreamTranslator Logs", fullText)
                    clipboard?.setPrimaryClip(clip)
                    val toastMsg = "Đã sao chép ${filteredEntries.size} dòng log"
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                },
            ) {
                Text("Sao chép")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        },
    )
}
