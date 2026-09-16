package com.example.autoring.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoring.audio.AudioProfileManager
import com.example.autoring.audio.SystemAudioStatus
import com.example.autoring.data.AppDatabase
import com.example.autoring.data.Profile
import com.example.autoring.data.ProfileRepository
import com.example.autoring.data.Schedule
import com.example.autoring.model.DaysOfWeek
import com.example.autoring.scheduler.ProfileScheduler
import com.example.autoring.update.UpdateConfig
import com.example.autoring.update.UpdateInfo
import com.example.autoring.update.UpdateManager
import com.example.autoring.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AutoRing"
    }

    private val app: Application = application

    // repo/audio 必须等数据库建好后再用；这里用 lateinit，在 init 的 IO 协程里初始化，
    // 绝不在主线程（ViewModel 构造时）访问 Room，避免 ANR（尤其 Realme 等慢机）。
    private lateinit var repo: ProfileRepository
    private lateinit var audio: AudioProfileManager

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules.asStateFlow()

    private val _systemStatus = MutableStateFlow(SystemAudioStatus())
    val systemStatus: StateFlow<SystemAudioStatus> = _systemStatus

    private val _activeProfileName = MutableStateFlow("—")
    val activeProfileName: StateFlow<String> = _activeProfileName

    private val _policyAccess = MutableStateFlow(false)
    val policyAccess: StateFlow<Boolean> = _policyAccess.asStateFlow()

    // —— 调度诊断（让「为什么不自动切换」可自查）——
    private val _nextSwitchText = MutableStateFlow("—")
    val nextSwitchText: StateFlow<String> = _nextSwitchText.asStateFlow()

    private val _lastApplyText = MutableStateFlow("—")
    val lastApplyText: StateFlow<String> = _lastApplyText.asStateFlow()

    private val _exactAlarm = MutableStateFlow(false)
    val exactAlarm: StateFlow<Boolean> = _exactAlarm.asStateFlow()

    private val _batteryOptimized = MutableStateFlow(true)
    val batteryOptimized: StateFlow<Boolean> = _batteryOptimized.asStateFlow()

    // —— 在线升级 ——
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage

    /** Android 8+ 是否允许安装未知应用（授权后从设置返回会刷新） */
    private val _canInstallPackages = MutableStateFlow(true)
    val canInstallPackages: StateFlow<Boolean> = _canInstallPackages

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val r = ProfileRepository(AppDatabase.get(app))
            val a = AudioProfileManager(app)
            repo = r
            audio = a
            // 首次启动塞入两个示例模式 + 时间段（贴合你的真实场景），可随意删除
            seedExamplesIfNeeded(r)
            _ready.value = true
            // 两个流各自后台收集，UI 订阅即开始
            launch { r.observeProfiles().collect { _profiles.value = it } }
            launch { r.observeSchedules().collect { _schedules.value = it } }
            // 关键修复：冷启动也必须重新排程。
            // 旧代码这里只 refreshAll()（刷新界面状态）而从不 scheduleNext()，
            // 于是「开机没收到 BOOT_COMPLETED」或「alarm 被系统清掉」之后，
            // 用户打开 App 也不会恢复调度 —— 表现就是彻底不自动切换。
            rescheduleAndApply()
            // 冷启动时静默检查一次更新（24 小时内不重复打扰）
            checkForUpdate(manual = false)
        }
    }

    /**
     * 预置示例数据。用 seed_version 而非一次性布尔，方便后续升级补充。
     * v1：睡眠 / 上班 两个模式 + 对应时段。
     * v2：补一个语义清晰的「日常·常规」作为兜底默认 ——
     *     v1 把「睡眠·闹钟最大」设为默认，导致白天（无时段匹配时）也套用睡眠参数，语义混乱。
     */
    private suspend fun seedExamplesIfNeeded(r: ProfileRepository) {
        val prefs = app.getSharedPreferences("autoring_prefs", Context.MODE_PRIVATE)
        val version = prefs.getInt("seed_version", 0)
        try {
            // 用 suspend 直接查询，不用 Flow.first()：
            // Room 数据库首次打开时 Flow 的首次发射时序不可靠，曾导致这里误判为「已有数据」
            // 从而跳过创建，表现为首次打开一片空白、永远不自动切换。
            val existing = r.getAllProfiles()
            Log.i(TAG, "seed: version=$version 现有模式=${existing.size}")

            // 兜底：只要模式表为空就重建示例（数据丢失时自愈，避免打开就是空白）
            if (existing.isEmpty()) {
                val sleepId = r.addProfile(
                    Profile(name = "睡眠·闹钟最大", alarmVolumePercent = 100, ringerMode = "NORMAL", vibration = false)
                )
                val workId = r.addProfile(
                    Profile(name = "上班·静音震动", alarmVolumePercent = 70, ringerMode = "VIBRATE", vibration = true)
                )
                Log.i(TAG, "seed: 创建模式 sleep=$sleepId work=$workId")
                val s1 = r.addSchedule(
                    Schedule(profileId = sleepId, startMinute = 22 * 60 + 30, endMinute = 7 * 60, daysOfWeek = DaysOfWeek.ALL)
                )
                val s2 = r.addSchedule(
                    Schedule(profileId = workId, startMinute = 9 * 60, endMinute = 18 * 60, daysOfWeek = DaysOfWeek.WEEKDAYS)
                )
                Log.i(TAG, "seed: 创建时间段 $s1 $s2")
            }

            // 兜底默认：任何时段都不匹配时套用。没有它，时段结束后不会回落。
            if (r.getAllProfiles().none { it.name == "日常·常规" }) {
                val dailyId = r.addProfile(
                    Profile(name = "日常·常规", alarmVolumePercent = 70, ringerMode = "NORMAL", vibration = false)
                )
                r.setDefaultProfile(dailyId)
                Log.i(TAG, "seed: 创建兜底默认 日常·常规=$dailyId")
            }

            val p = r.getAllProfiles().size
            val s = r.getAllSchedules().size
            Log.i(TAG, "seed 完成: 模式=$p 时间段=$s")
            if (p == 0 || s == 0) Log.w(TAG, "seed 异常：模式或时间段仍为空")
            prefs.edit().putInt("seed_version", 2).apply()
        } catch (e: Exception) {
            Log.e(TAG, "seed 失败", e)
        }
    }

    /** 数据库/音频是否就绪 */
    private fun ready() = _ready.value

    fun refreshSystemStatus() {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _systemStatus.value = audio.currentStatus()
                _policyAccess.value = audio.hasNotificationPolicyAccess()
                _canInstallPackages.value = UpdateManager.canInstallPackages(app)
                _activeProfileName.value = ProfileScheduler.activeProfileName(app)
                    ?: "（当前无匹配模式）"
            } catch (e: Exception) {
                Log.e("AutoRing", "刷新系统状态失败", e)
            }
        }
    }

    /**
     * 幂等地「应用当前该生效的模式 + 排下一次切换」。
     * 冷启动、回到前台、数据变更后都调用，保证 alarm 链不会断。
     */
    fun rescheduleAndApply() {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ProfileScheduler.applyCurrent(app)
                ProfileScheduler.scheduleNext(app)
                refreshSystemStatus()
                refreshDiagnostics()
            } catch (e: Exception) {
                Log.e("AutoRing", "重新排程/应用失败", e)
            }
        }
    }

    /** App 回到前台：补一次排程（幂等）+ 刷新界面，防止 alarm 链断掉 */
    fun onResume() {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ProfileScheduler.scheduleNext(app)
            } catch (e: Exception) {
                Log.e("AutoRing", "回前台重排失败", e)
            }
            refreshSystemStatus()
            refreshDiagnostics()
        }
    }

    /** 刷新「调度诊断」信息，让用户能自查为什么没切换 */
    private fun refreshDiagnostics() {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val next = ProfileScheduler.nextSwitchMillis(app)
                _nextSwitchText.value =
                    if (next > 0) ProfileScheduler.formatTime(next) else "（无切换点）"
                val last = ProfileScheduler.lastApplyMillis(app)
                _lastApplyText.value =
                    if (last > 0) ProfileScheduler.formatTime(last) else "（尚未应用）"
                _exactAlarm.value = ProfileScheduler.hasExactAlarmPermission(app)
                _batteryOptimized.value = !isIgnoringBatteryOptimizations(app)
            } catch (e: Exception) {
                Log.e("AutoRing", "刷新诊断信息失败", e)
            }
        }
    }

    /** 是否已加入电池优化白名单（true = 不被限制） */
    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun testVibration() {
        if (!ready()) return
        try {
            audio.testVibration()
        } catch (e: Exception) {
            Log.e("AutoRing", "测试震动失败", e)
        }
    }

    private fun refreshAll() = refreshSystemStatus()

    /** 任意增删改之后：立即应用当前应生效的模式 + 重排下一次触发 + 刷新状态 */
    private fun afterChange() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ProfileScheduler.applyCurrent(app)
                ProfileScheduler.scheduleNext(app)
                refreshSystemStatus()
                refreshDiagnostics()
            } catch (e: Exception) {
                Log.e("AutoRing", "应用/重排失败", e)
            }
        }
    }

    fun addProfile(name: String, alarm: Int, ringer: String, vibration: Boolean) {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.addProfile(
                Profile(
                    name = name,
                    alarmVolumePercent = alarm,
                    ringerMode = ringer,
                    vibration = vibration
                )
            )
            afterChange()
        }
    }

    fun updateProfile(profile: Profile) {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateProfile(profile)
            afterChange()
        }
    }

    fun setDefaultProfile(id: Long) {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.setDefaultProfile(id)
            afterChange()
        }
    }

    fun deleteProfile(profile: Profile) {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteProfile(profile)
            afterChange()
        }
    }

    fun addSchedule(schedule: Schedule) {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.addSchedule(schedule)
            afterChange()
        }
    }

    fun updateSchedule(schedule: Schedule) {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.updateSchedule(schedule)
            afterChange()
        }
    }

    fun deleteSchedule(schedule: Schedule) {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteSchedule(schedule)
            afterChange()
        }
    }

    fun applyProfile(profile: Profile) {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 手动点「应用」→ 给一次震动确认
                audio.applyProfile(profile, feedbackVibrate = true)
                refreshSystemStatus()
            } catch (e: Exception) {
                Log.e("AutoRing", "手动应用模式失败", e)
            }
        }
    }

    fun applyCurrentSchedule() {
        if (!ready()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ProfileScheduler.applyCurrent(app)
                ProfileScheduler.scheduleNext(app)
                refreshSystemStatus()
            } catch (e: Exception) {
                Log.e("AutoRing", "立即应用失败", e)
            }
        }
    }

    fun requestExactAlarmPermission() {
        ProfileScheduler.requestExactAlarmPermission(app)
    }

    fun requestIgnoreBatteryOptimizations() {
        ProfileScheduler.requestIgnoreBatteryOptimizations(app)
    }

    // ===== 在线升级 =====

    /** 检查更新。manual=true 时忽略节流，并给出「已是最新」的明确反馈 */
    fun checkForUpdate(manual: Boolean = false) {
        val prefs = app.getSharedPreferences("autoring_prefs", Context.MODE_PRIVATE)
        val last = prefs.getLong("last_update_check", 0L)
        if (!manual && System.currentTimeMillis() - last < UpdateConfig.AUTO_CHECK_INTERVAL_MS) return

        _updateState.value = UpdateState.Checking
        _updateMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val info = UpdateManager.checkForUpdate()
            prefs.edit().putLong("last_update_check", System.currentTimeMillis()).apply()
            _updateInfo.value = info
            _updateState.value = UpdateState.Idle
            if (manual && info == null) {
                _updateMessage.value = "已是最新版本（${UpdateManager.currentVersion()}）"
            }
        }
    }

    /**
     * 下载新版本并拉起安装。
     * 这是**覆盖安装**：同包名 + 同签名时系统只替换 APK，
     * /data 下的数据库与配置原样保留 —— 即「升级保留原配置」。
     */
    fun downloadAndInstall() {
        val info = _updateInfo.value ?: return
        // Android 8+ 没有「允许安装未知应用」权限时，安装界面根本打不开，先拦下来
        if (!UpdateManager.canInstallPackages(app)) {
            _updateState.value = UpdateState.Failed("需要先授予「允许安装未知应用」权限")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _updateState.value = UpdateState.Downloading(0)
                val file = UpdateManager.download(app, info) { p ->
                    _updateState.value = UpdateState.Downloading(p)
                }
                _updateState.value = UpdateState.Ready(file)
                // 下载完成直接拉起系统安装界面（覆盖安装，配置保留）
                UpdateManager.install(app, file)
            } catch (e: Exception) {
                Log.e(TAG, "下载更新失败", e)
                _updateState.value = UpdateState.Failed(e.message ?: "下载失败")
            }
        }
    }

    fun installUpdate() {
        (_updateState.value as? UpdateState.Ready)?.let {
            UpdateManager.install(app, it.file)
        }
    }

    fun clearUpdateMessage() {
        _updateMessage.value = null
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    fun openInstallPermissionSettings() = UpdateManager.openInstallPermissionSettings(app)

    fun currentVersion(): String = UpdateManager.currentVersion()

    fun hasPolicyAccess(): Boolean = if (ready()) audio.hasNotificationPolicyAccess() else false

    fun hasVibrator(): Boolean = if (ready()) audio.hasVibrator() else false
}
