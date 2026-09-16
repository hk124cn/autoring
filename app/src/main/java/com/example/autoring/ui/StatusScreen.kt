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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.autoring.audio.SystemAudioStatus

@Composable
fun StatusScreen(
    systemStatus: SystemAudioStatus,
    activeProfileName: String,
    nextSwitchText: String,
    lastApplyText: String,
    exactAlarm: Boolean,
    batteryOptimized: Boolean,
    onApplyCurrent: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 当前生效模式高亮卡
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("当前生效模式", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(4.dp))
                Text(
                    activeProfileName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // 自动切换诊断卡：让「为什么不自动切换」可自查
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("自动切换诊断", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("下次切换", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(76.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(nextSwitchText, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("上次应用", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(76.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(lastApplyText, style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("精确闹钟", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(76.dp))
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(
                        ok = exactAlarm,
                        okText = "已授权（准点）",
                        badText = "未授权（可能延迟）"
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("电池优化", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(76.dp))
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(
                        ok = !batteryOptimized,
                        okText = "已加白名单",
                        badText = "受限制（建议加白）"
                    )
                }
            }
        }

        // 系统音量卡
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("系统音量", style = MaterialTheme.typography.titleMedium)
                // 闹钟音量
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Tune, null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("闹钟", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(48.dp))
                    LinearProgressIndicator(
                        progress = { if (systemStatus.alarmMax > 0) systemStatus.alarmVolume.toFloat() / systemStatus.alarmMax else 0f },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${systemStatus.alarmVolume}/${systemStatus.alarmMax}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                // 来电音量
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(ringerIcon(systemStatus.ringerMode), null, Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("来电", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(48.dp))
                    LinearProgressIndicator(
                        progress = { if (systemStatus.ringMax > 0) systemStatus.ringVolume.toFloat() / systemStatus.ringMax else 0f },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${systemStatus.ringVolume}/${systemStatus.ringMax}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("铃声模式", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(64.dp))
                    Spacer(Modifier.width(8.dp))
                    ModeBadge(systemStatus.ringerMode)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("勿扰权限", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(64.dp))
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(
                        ok = systemStatus.policyAccess,
                        okText = "已授予",
                        badText = "未授予"
                    )
                }
            }
        }

        Button(onClick = onApplyCurrent, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("立即应用当前时段对应模式")
        }
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("刷新状态")
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "说明：修改模式 / 时间段后，到时间点会自动切换；也可点「立即应用」马上生效。" +
                "建议在此核对闹钟 / 来电音量数字是否如预期变化。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
