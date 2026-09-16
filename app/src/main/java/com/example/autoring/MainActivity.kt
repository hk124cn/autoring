package com.example.autoring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.autoring.ui.MainViewModel
import com.example.autoring.ui.PermissionGuideScreen
import com.example.autoring.ui.ProfilesScreen
import com.example.autoring.ui.SchedulesScreen
import com.example.autoring.ui.StatusScreen
import com.example.autoring.ui.theme.AutoRingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoRingTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: MainViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val systemStatus by viewModel.systemStatus.collectAsStateWithLifecycle()
    val activeName by viewModel.activeProfileName.collectAsStateWithLifecycle()
    val policyAccess by viewModel.policyAccess.collectAsStateWithLifecycle()
    val nextSwitchText by viewModel.nextSwitchText.collectAsStateWithLifecycle()
    val lastApplyText by viewModel.lastApplyText.collectAsStateWithLifecycle()
    val exactAlarm by viewModel.exactAlarm.collectAsStateWithLifecycle()
    val batteryOptimized by viewModel.batteryOptimized.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val updateMessage by viewModel.updateMessage.collectAsStateWithLifecycle()
    val canInstall by viewModel.canInstallPackages.collectAsStateWithLifecycle()

    // 每次回到前台都补一次排程（幂等），防止 alarm 链被系统清掉后静默失效
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tabs = listOf(
        "模式" to Icons.Filled.List,
        "时间段" to Icons.Filled.Schedule,
        "状态" to Icons.Filled.Tune,
        "设置" to Icons.Filled.Settings
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoRing · ${tabs[tab].first}") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            if (i == 0 || i == 2) viewModel.refreshSystemStatus()
                        },
                        label = { Text(label) },
                        icon = { Icon(icon, contentDescription = null) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        when (tab) {
            0 -> ProfilesScreen(
                profiles = profiles,
                onAdd = viewModel::addProfile,
                onUpdate = viewModel::updateProfile,
                onDelete = viewModel::deleteProfile,
                onApply = viewModel::applyProfile,
                onSetDefault = viewModel::setDefaultProfile,
                onTestVibration = viewModel::testVibration
            )
            1 -> SchedulesScreen(
                schedules = schedules,
                profiles = profiles,
                onAdd = viewModel::addSchedule,
                onUpdate = viewModel::updateSchedule,
                onDelete = viewModel::deleteSchedule
            )
            2 -> StatusScreen(
                systemStatus = systemStatus,
                activeProfileName = activeName,
                nextSwitchText = nextSwitchText,
                lastApplyText = lastApplyText,
                exactAlarm = exactAlarm,
                batteryOptimized = batteryOptimized,
                onApplyCurrent = viewModel::applyCurrentSchedule,
                onRefresh = viewModel::refreshSystemStatus
            )
            3 -> PermissionGuideScreen(
                hasPolicyAccess = policyAccess,
                hasVibrator = viewModel.hasVibrator(),
                exactAlarm = exactAlarm,
                batteryOptimized = batteryOptimized,
                currentVersion = viewModel.currentVersion(),
                updateState = updateState,
                updateInfo = updateInfo,
                updateMessage = updateMessage,
                canInstallPackages = canInstall,
                onRequestExactAlarm = viewModel::requestExactAlarmPermission,
                onRequestBattery = viewModel::requestIgnoreBatteryOptimizations,
                onCheckUpdate = { viewModel.checkForUpdate(manual = true) },
                onUpgrade = viewModel::downloadAndInstall,
                onRequestInstallPermission = viewModel::openInstallPermissionSettings,
                onResume = viewModel::refreshSystemStatus
            )
        }
        }
    }
}
