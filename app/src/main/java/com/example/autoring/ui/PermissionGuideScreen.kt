package com.example.autoring.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.autoring.update.UpdateInfo
import com.example.autoring.update.UpdateState

@Composable
fun PermissionGuideScreen(
    hasPolicyAccess: Boolean,
    hasVibrator: Boolean,
    exactAlarm: Boolean,
    batteryOptimized: Boolean,
    currentVersion: String,
    updateState: UpdateState,
    updateInfo: UpdateInfo?,
    updateMessage: String?,
    canInstallPackages: Boolean,
    onRequestExactAlarm: () -> Unit,
    onRequestBattery: () -> Unit,
    onCheckUpdate: () -> Unit,
    onUpgrade: () -> Unit,
    onRequestInstallPermission: () -> Unit,
    onResume: () -> Unit
) {
    val context = LocalContext.current

    // 从系统设置返回本页时，刷新权限徽章
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("权限与保活设置", style = MaterialTheme.typography.titleLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("勿扰权限", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusBadge(ok = hasPolicyAccess, okText = "已授予", badText = "未授予")
                }
                Text("控制来电 / 通知的响铃、震动、静音模式，必须授予。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    openNotificationPolicySettings(context)
                }, modifier = Modifier.fillMaxWidth()) { Text("开启勿扰权限") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("精确闹钟权限（Android 12+）", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    StatusBadge(ok = exactAlarm, okText = "已授权", badText = "未授权")
                }
                Text("用于准点触发切换。未授予会降级为模糊定时，可能延迟几分钟。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRequestExactAlarm, modifier = Modifier.fillMaxWidth()) { Text("请求精确闹钟权限") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("忽略电池优化", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    StatusBadge(ok = !batteryOptimized, okText = "已加白名单", badText = "受限制")
                }
                Text("防止系统杀后台导致定时不触发。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onRequestBattery, modifier = Modifier.fillMaxWidth()) { Text("忽略电池优化") }
            }
        }

        // 版本与在线升级
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("版本与更新", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    Text("v$currentVersion", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (val st = updateState) {
                    is UpdateState.Checking ->
                        Text("正在检查更新…", style = MaterialTheme.typography.bodySmall)
                    is UpdateState.Downloading -> {
                        Text("下载中 ${st.progress}%", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { st.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is UpdateState.Failed ->
                        Text("更新失败：${st.message}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    else -> {
                        if (updateInfo != null) {
                            Text("发现新版本 ${updateInfo.versionName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        } else if (updateMessage != null) {
                            Text(updateMessage, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(
                    onClick = onCheckUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = updateState !is UpdateState.Checking &&
                        updateState !is UpdateState.Downloading
                ) { Text("检查更新") }

                if (!canInstallPackages) {
                    Text(
                        "在线升级需要「允许安装未知应用」权限，否则无法拉起安装界面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = onRequestInstallPermission, modifier = Modifier.fillMaxWidth()) {
                        Text("去授权安装权限")
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Settings, null, Modifier.padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "震动支持：${if (hasVibrator) "本机有震动马达" else "无震动马达"}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text("Realme / ColorOS 额外必做（系统设置，无统一入口）：", style = MaterialTheme.typography.titleMedium)
        Text("1. 手机管家 → 权限隐私 → 自启动管理：允许本 App 自启动。")
        Text("2. 手机管家 → 电池 → 应用耗电管理 → 本 App：设为「允许后台运行 / 不限制」。")
        Text("3. 设置 → 通知与状态栏 → 通知管理：允许本 App 通知。")
        Text("不设置这三项，定时可能不会触发。", style = MaterialTheme.typography.bodySmall)
    }

    // 检测到新版本 → 弹出升级对话框
    updateInfo?.let { info ->
        var dismissed by remember(info.tagName) { mutableStateOf(false) }
        if (!dismissed) {
            AlertDialog(
                onDismissRequest = { dismissed = true },
                title = { Text("发现新版本 ${info.versionName}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (info.changelog.isNotBlank()) {
                            Text(info.changelog, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "升级为覆盖安装，不会清除你的模式和时间段配置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        when (val st = updateState) {
                            is UpdateState.Downloading -> {
                                Text("下载中 ${st.progress}%", style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(
                                    progress = { st.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            is UpdateState.Failed -> Text(
                                "失败：${st.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            else -> {}
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = onUpgrade,
                        enabled = updateState !is UpdateState.Downloading
                    ) {
                        Text(if (updateState is UpdateState.Failed) "重试" else "立即升级")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dismissed = true }) { Text("稍后") }
                }
            )
        }
    }
}

/**
 * 安全地打开「勿扰权限访问」系统设置页。
 * - 必须带 FLAG_ACTIVITY_NEW_TASK，否则当 LocalContext 不是 Activity 时会抛
 *   AndroidRuntimeException 直接把 App 干掉（这正是 Realme/ColorOS 上点击崩溃的原因）。
 * - 该 Action 在个别机型上可能没有对应 Activity，需 try/catch 兜底到应用详情页并提示用户手动开启。
 */
private fun openNotificationPolicySettings(context: android.content.Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        try {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(fallback)
            Toast.makeText(
                context,
                "未找到勿扰设置入口，请在「应用信息」里开启「勿扰权限访问」",
                Toast.LENGTH_LONG
            ).show()
        } catch (e2: Exception) {
            Toast.makeText(context, "请到系统设置手动授予勿扰权限", Toast.LENGTH_LONG).show()
        }
    }
}
