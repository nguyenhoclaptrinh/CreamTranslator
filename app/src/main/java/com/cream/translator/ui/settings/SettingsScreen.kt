package com.cream.translator.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cream.translator.data.repository.FilterPreferencesRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: FilterPreferencesRepository,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current
    val config by repository.configFlow.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    BackHandler { onNavigateBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt bộ lọc từ") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("← Quay lại", style = MaterialTheme.typography.bodyLarge)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MasterSwitchCard(
                isFilteringEnabled = config.isFilteringEnabled,
                onToggle = { repository.setFilteringEnabled(it) },
            )

            UiControlsCard(
                isUiControlsEnabled = config.isUiControlsEnabled,
                isEnabled = config.isFilteringEnabled,
                onToggle = { repository.setUiControlsEnabled(it) },
            )

            SymbolsAndNumbersCard(
                isSymbolsAndNumbersEnabled = config.isSymbolsAndNumbersEnabled,
                isEnabled = config.isFilteringEnabled,
                onToggle = { repository.setSymbolsAndNumbersEnabled(it) },
            )

            CustomWordsCard(
                customWords = config.customBlockedWords,
                isEnabled = config.isFilteringEnabled,
                onAddWord = { word ->
                    val success = repository.addCustomWord(word)
                    if (success) {
                        Toast.makeText(context, "Đã thêm '$word' vào danh sách bỏ qua", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Từ '$word' đã tồn tại trong danh sách", Toast.LENGTH_SHORT).show()
                    }
                },
                onRemoveWord = { word ->
                    repository.removeCustomWord(word)
                    Toast.makeText(context, "Đã xóa '$word'", Toast.LENGTH_SHORT).show()
                },
            )

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Khôi phục bộ lọc về mặc định")
            }

            Spacer(modifier = Modifier.height(8.dp))

            DiagnosticsCard()

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (showResetDialog) {
            ResetFilterDialog(
                onConfirm = {
                    repository.resetToDefaults()
                    showResetDialog = false
                    Toast.makeText(context, "Đã khôi phục cài đặt mặc định", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showResetDialog = false },
            )
        }
    }
}

@Composable
private fun MasterSwitchCard(
    isFilteringEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Bật bộ lọc thông minh",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Switch(
                    checked = isFilteringEnabled,
                    onCheckedChange = onToggle,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    "Tự động bỏ qua không dịch các từ UI quá phổ biến, " +
                        "số và ký hiệu để giữ giao diện gốc sạch sẽ và tăng tốc độ xử lý.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UiControlsCard(
    isUiControlsEnabled: Boolean,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Nút bấm & Điều hướng UI",
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = isUiControlsEnabled,
                    onCheckedChange = onToggle,
                    enabled = isEnabled,
                )
            }
            Text(
                text =
                    "Bỏ qua các từ điều khiển phổ biến như: " +
                        "OK, Cancel, Next, Back, Menu, Settings, Home, Exit, Save, Skip...",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val uiSamples =
                    listOf("OK", "Cancel", "Next", "Back", "Home", "Menu", "Settings", "Skip", "Save", "Exit")
                uiSamples.forEach { sample ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(sample) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymbolsAndNumbersCard(
    isSymbolsAndNumbersEnabled: Boolean,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ký hiệu, Tỷ lệ & Số",
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = isSymbolsAndNumbersEnabled,
                    onCheckedChange = onToggle,
                    enabled = isEnabled,
                )
            }
            Text(
                text =
                    "Bỏ qua các số thuần túy, %, mốc thời gian, " +
                        "tiền tệ, đơn vị đo và chỉ số game (Lv, HP, MP, EXP).",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val symbolSamples =
                    listOf("123", "99%", "$500", "12:00", "Lv.99", "HP 500", "60fps", "1080px")
                symbolSamples.forEach { sample ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(sample) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomWordsCard(
    customWords: Set<String>,
    isEnabled: Boolean,
    onAddWord: (String) -> Unit,
    onRemoveWord: (String) -> Unit,
) {
    var newWordText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Từ điển tùy biến",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Thêm các từ hoặc thuật ngữ riêng bạn muốn giữ nguyên chữ gốc khi chụp màn hình.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newWordText,
                    onValueChange = { newWordText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ví dụ: Gacha, Buff, Nerf") },
                    singleLine = true,
                    enabled = isEnabled,
                )
                Button(
                    onClick = {
                        val trimmed = newWordText.trim()
                        if (trimmed.isNotBlank()) {
                            onAddWord(trimmed)
                            newWordText = ""
                        }
                    },
                    enabled = isEnabled && newWordText.isNotBlank(),
                ) {
                    Text("Thêm")
                }
            }

            if (customWords.isEmpty()) {
                Text(
                    text = "Chưa có từ tùy biến nào được thêm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    customWords.forEach { word ->
                        InputChip(
                            selected = false,
                            onClick = { onRemoveWord(word) },
                            label = { Text(word) },
                            trailingIcon = { Text("✕", style = MaterialTheme.typography.bodyMedium) },
                            enabled = isEnabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetFilterDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Khôi phục cài đặt gốc") },
        text = {
            Text(
                "Bạn có chắc chắn muốn đặt lại tất cả bộ lọc " +
                    "và xóa danh sách từ tùy biến về trạng thái mặc định ban đầu?",
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
    )
}
