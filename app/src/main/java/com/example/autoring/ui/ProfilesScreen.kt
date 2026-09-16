package com.example.autoring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autoring.data.Profile
import com.example.autoring.model.RingerMode

@Composable
fun ProfilesScreen(
    profiles: List<Profile>,
    onAdd: (String, Int, String, Boolean) -> Unit,
    onUpdate: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onApply: (Profile) -> Unit,
    onSetDefault: (Long) -> Unit,
    onTestVibration: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (profiles.isEmpty()) {
            EmptyState(Icons.Filled.List, "还没有模式，点击下方按钮新增第一个")
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(profiles) { p ->
                    ProfileCard(
                        p,
                        onApply = { onApply(p) },
                        onEdit = { editing = p },
                        onDelete = { onDelete(p) },
                        onSetDefault = { onSetDefault(p.id) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        FilledTonalButton(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("新增模式")
        }
    }

    if (showDialog) {
        ProfileDialog(
            initial = null,
            onDismiss = { showDialog = false },
            onConfirm = { name, alarm, ringer, vib ->
                onAdd(name, alarm, ringer, vib)
                showDialog = false
            },
            onTestVibration = onTestVibration
        )
    }
    editing?.let { p ->
        ProfileDialog(
            initial = p,
            onDismiss = { editing = null },
            onConfirm = { name, alarm, ringer, vib ->
                onUpdate(p.copy(name = name, alarmVolumePercent = alarm, ringerMode = ringer, vibration = vib))
                editing = null
            },
            onTestVibration = onTestVibration
        )
    }
}

@Composable
private fun ProfileCard(
    p: Profile,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (p.isDefault) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (p.isDefault) {
                    AssistBadgeText("默认")
                }
            }
            Spacer(Modifier.height(8.dp))
            // 闹钟音量进度条
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Notifications, null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { p.alarmVolumePercent / 100f },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${p.alarmVolumePercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeBadge(p.ringerMode)
                Spacer(Modifier.width(8.dp))
                if (p.vibration) {
                    AssistChipText("震动反馈")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "优先级 ${p.priority}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(onClick = onApply) { Text("立即应用") }
                if (!p.isDefault) {
                    TextButton(onClick = onSetDefault) { Text("设为默认") }
                }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "编辑") }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "删除") }
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    initial: Profile?,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, String, Boolean) -> Unit,
    onTestVibration: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var alarm by remember { mutableStateOf(initial?.alarmVolumePercent ?: 80) }
    var ringer by remember { mutableStateOf(initial?.ringerMode ?: "NORMAL") }
    var vibration by remember { mutableStateOf(initial?.vibration ?: false) }

    AlertDialogWrapped(
        onDismiss = onDismiss,
        confirmText = if (initial == null) "新增" else "保存",
        onConfirm = { if (name.isNotBlank()) onConfirm(name, alarm, ringer, vibration) },
        title = if (initial == null) "新增模式" else "编辑模式"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("闹钟音量", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { alarm / 100f },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("$alarm%", style = MaterialTheme.typography.labelMedium)
            }
            SliderValue(
                value = alarm.toFloat(),
                onValueChange = { alarm = it.toInt() },
                valueRange = 0f..100f
            )
            Text("来电 / 通知模式", style = MaterialTheme.typography.labelMedium)
            Row {
                RingerMode.entries.forEach {
                    FilterChipSelected(
                        selected = ringer == it.value,
                        onClick = { ringer = it.value },
                        label = it.label
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SwitchBox(checked = vibration, onCheckedChange = { vibration = it })
                Text("震动反馈", modifier = Modifier.padding(end = 4.dp))
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onTestVibration) { Text("测试震动") }
            }
            Text(
                "「震动反馈」仅在你手动点「立即应用」时震一下，用来确认模式已切换；" +
                    "「测试震动」可立刻验证本机马达是否正常。定时/开机自动切换不会震动，避免深夜吵醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
