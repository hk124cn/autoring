package com.example.autoring.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 铃声模式对应的图标 */
fun ringerIcon(mode: String): ImageVector = when (mode) {
    "VIBRATE" -> Icons.Filled.Vibration
    "SILENT" -> Icons.Filled.NotificationsOff
    "RING_VIBRATE" -> Icons.Filled.NotificationsActive
    else -> Icons.Filled.Notifications
}

/** 铃声模式对应的中文标签 */
fun ringerLabel(mode: String): String = when (mode) {
    "VIBRATE" -> "震动"
    "SILENT" -> "静音"
    "RING_VIBRATE" -> "响铃+震动"
    else -> "响铃"
}

/** 模式彩色徽章（如「响铃」「响铃+震动」「震动」「静音」） */
@Composable
fun ModeBadge(mode: String) {
    val (icon, container, content) = when (mode) {
        "RING_VIBRATE" -> Triple(Icons.Filled.NotificationsActive, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        "VIBRATE" -> Triple(Icons.Filled.Vibration, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        "SILENT" -> Triple(Icons.Filled.NotificationsOff, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        else -> Triple(Icons.Filled.Notifications, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    }
    AssistChip(
        onClick = { },
        enabled = true,
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        label = { Text(ringerLabel(mode)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = content,
            leadingIconContentColor = content
        )
    )
}

/** 权限/状态达成徽章：绿色对勾或红色警示 */
@Composable
fun StatusBadge(ok: Boolean, okText: String, badText: String) {
    val (icon, container, content) = if (ok) {
        Triple(Icons.Filled.CheckCircle, Color(0xFFE6F4EA), Color(0xFF1E7E34))
    } else {
        Triple(Icons.Filled.Error, Color(0xFFFCE8E6), Color(0xFFC5221F))
    }
    AssistChip(
        onClick = { },
        enabled = true,
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        label = { Text(if (ok) okText else badText) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = content,
            leadingIconContentColor = content
        )
    )
}

/** 空列表占位插画 */
@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.size(96.dp)
        ) {
            Icon(
                icon,
                null,
                Modifier.size(48.dp).padding(0.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 小标题 + 内容行 */
@Composable
fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        content()
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** 小号实心徽章（如「默认」标记） */
@Composable
fun AssistBadgeText(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** 小号柔色标签（如「震动反馈」「跨午夜」） */
@Composable
fun AssistChipText(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** 统一风格的对话框外壳 */
@Composable
fun AlertDialogWrapped(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText) }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    )
}

/** 带数值显示的横滑滑块 */
@Composable
fun SliderValue(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
}

@Composable
fun FilterChipSelected(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
fun SwitchBox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}
