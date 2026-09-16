package com.example.autoring.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.autoring.MainActivity
import com.example.autoring.audio.AudioProfileManager
import com.example.autoring.data.AppDatabase
import com.example.autoring.data.Profile
import com.example.autoring.data.Schedule
import com.example.autoring.model.DaysOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ProfileScheduler {
    const val ACTION_SWITCH = "com.example.autoring.ACTION_SWITCH"
    const val ACTION_APPLY_CURRENT = "com.example.autoring.ACTION_APPLY_CURRENT"

    private const val TAG = "AutoRing"
    private const val REQ_SWITCH = 1001
    private const val REQ_WATCHDOG = 1002
    private const val REQ_CONTENT = 1003
    /** 看门狗间隔：即使某次切换被系统吞掉/推迟，最多 15 分钟内会被补正 */
    private const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L

    private const val PREFS = "autoring_prefs"
    const val KEY_NEXT_SWITCH = "next_switch_ms"
    const val KEY_LAST_APPLY = "last_apply_ms"
    const val KEY_LAST_SCHEDULE = "last_schedule_ms"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun resolveCurrent(
        now: Calendar,
        schedules: List<Schedule>,
        profiles: List<Profile>,
        defaultProfile: Profile?
    ): Profile? {
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val dayBit = DaysOfWeek.bitForCalendarDay(now.get(Calendar.DAY_OF_WEEK))
        val matched = schedules
            .filter { it.enabled && (it.daysOfWeek and dayBit) != 0 && inRange(it, minute) }
            .mapNotNull { s -> profiles.firstOrNull { p -> p.id == s.profileId } }
        return matched.maxByOrNull { it.priority } ?: defaultProfile
    }

    private fun inRange(s: Schedule, minute: Int): Boolean {
        return if (s.startMinute <= s.endMinute) {
            minute in s.startMinute..s.endMinute
        } else {
            // 跨午夜：start > end，如 22:00-07:00
            minute >= s.startMinute || minute <= s.endMinute
        }
    }

    suspend fun applyCurrent(context: Context) = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val profiles = db.dao().getAllProfiles()
        val schedules = db.dao().getAllSchedules()
        val default = db.dao().getDefaultProfile()
        val current = resolveCurrent(Calendar.getInstance(), schedules, profiles, default)
        // 自动切换（定时/开机/看门狗）不震动，避免夜间切到睡眠模式时把人吵醒
        current?.let { AudioProfileManager(context).applyProfile(it, feedbackVibrate = false) }
        prefs(context).edit()
            .putLong(KEY_LAST_APPLY, System.currentTimeMillis())
            .putString("last_applied_profile", current?.name)
            .apply()
        Log.i(TAG, "applyCurrent -> ${current?.name ?: "（无匹配模式，保持现状）"}")
    }

    /** 返回当前时刻应生效的模式名称（用于状态页展示），无匹配且无默认时返回 null */
    suspend fun activeProfileName(context: Context): String? = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val current = resolveCurrent(
            Calendar.getInstance(),
            db.dao().getAllSchedules(),
            db.dao().getAllProfiles(),
            db.dao().getDefaultProfile()
        )
        current?.name
    }

    /** Android 12+ 必须检查精确闹钟权限，否则精确排程会抛 SecurityException */
    private fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** 供界面诊断展示：是否已具备精确闹钟能力 */
    fun hasExactAlarmPermission(context: Context): Boolean = canScheduleExactAlarms(context)

    /** 请求加入电池优化白名单（不加会被厂商省电策略限制定时触发） */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(context)) {
                runCatching {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    // 部分 ROM 不支持该弹窗，退回到电池优化列表页
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    suspend fun scheduleNext(context: Context) = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val db = AppDatabase.get(context)
        val next = computeNextSwitch(Calendar.getInstance(), db.dao().getAllSchedules())

        val switchPi = pendingIntent(context, REQ_SWITCH, ACTION_SWITCH)
        am.cancel(switchPi)
        if (next != null) {
            if (canScheduleExactAlarms(context)) {
                // setAlarmClock 是系统为「闹钟」场景保留的最高优先级 API：
                // 不受 Doze 节流影响，也不会像 setExactAndAllowWhileIdle 那样被推迟到维护窗口才触发。
                // 代价：状态栏会显示一个闹钟图标（点击可打开本 App）——对本场景合理。
                am.setAlarmClock(
                    AlarmManager.AlarmClockInfo(next.timeInMillis, contentIntent(context)),
                    switchPi
                )
            } else {
                // 无精确闹钟权限：降级为 inexact（可能延迟数分钟），由看门狗兜底补正
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, switchPi)
            }
        }
        // 无论精确 alarm 是否排上，都挂看门狗：
        // 若某次切换被系统吞掉或推迟，最多 15 分钟内会被补正并重新排程。
        scheduleWatchdog(context, am)

        prefs(context).edit()
            .putLong(KEY_NEXT_SWITCH, next?.timeInMillis ?: -1L)
            .putLong(KEY_LAST_SCHEDULE, System.currentTimeMillis())
            .apply()
        Log.i(TAG, "scheduleNext -> ${next?.let { formatTime(it.timeInMillis) } ?: "无切换点"}")
    }

    /**
     * 看门狗：周期性（15 分钟）幂等执行「应用当前该生效的模式 + 重排」。
     * 目的：即使某个精确切换点被 Doze 推迟、被厂商省电策略吞掉，
     * 或 App 进程被杀后 BroadcastReceiver 没能跑完，也能在 15 分钟内自动纠正回来。
     */
    private fun scheduleWatchdog(context: Context, am: AlarmManager) {
        val pi = pendingIntent(context, REQ_WATCHDOG, ACTION_APPLY_CURRENT)
        am.cancel(pi)
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + WATCHDOG_INTERVAL_MS,
            pi
        )
    }

    private fun pendingIntent(context: Context, reqCode: Int, action: String): PendingIntent {
        val intent = Intent(context, ProfileAlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 状态栏闹钟图标被点击时打开本 App */
    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, REQ_CONTENT, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 供状态页诊断：下一次切换时刻（毫秒），无返回 -1 */
    fun nextSwitchMillis(context: Context): Long = prefs(context).getLong(KEY_NEXT_SWITCH, -1L)

    fun lastApplyMillis(context: Context): Long = prefs(context).getLong(KEY_LAST_APPLY, -1L)

    fun lastScheduleMillis(context: Context): Long = prefs(context).getLong(KEY_LAST_SCHEDULE, -1L)

    fun formatTime(millis: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(millis))

    /** 请求用户授予精确闹钟权限（Android 12+） */
    fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!canScheduleExactAlarms(context)) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * 计算 from 之后最近的一次「切换点」时刻。
     * 切换点 = 每个启用时间段的：开始时刻，以及结束时刻（结束点会让该模式关闭、回落到默认/无）。
     *
     * 关键点：跨午夜时段（start > end，如 22:00–07:00）的「结束点」落在**开始日 +1 天**，
     * 而不是「同星期的同一天」。旧实现把 end 也按 daysMask 找同一星期几，
     * 导致「仅周一 22:00–07:00」会一直开到「下周一 07:00」才关——这里修正为正确的次日结束。
     */
    private fun computeNextSwitch(from: Calendar, schedules: List<Schedule>): Calendar? {
        var best: Calendar? = null
        val cursor = from.clone() as Calendar
        for (offset in 0..7) {
            val day = cursor.clone() as Calendar
            day.set(Calendar.HOUR_OF_DAY, 0)
            day.set(Calendar.MINUTE, 0)
            day.set(Calendar.SECOND, 0)
            day.set(Calendar.MILLISECOND, 0)
            val dayBit = DaysOfWeek.bitForCalendarDay(day.get(Calendar.DAY_OF_WEEK))

            for (s in schedules.filter { it.enabled }) {
                if ((s.daysOfWeek and dayBit) == 0) continue
                // 开始切换点（本日开始时刻）
                val startC = atTime(day, s.startMinute)
                if (startC.timeInMillis > from.timeInMillis) best = earlierOf(best, startC)
                // 结束切换点
                if (s.startMinute <= s.endMinute) {
                    // 不跨午夜：结束点在本日 end+1 分（让 end 那一分钟仍处于该模式）
                    val endC = atTime(day, s.endMinute).apply { add(Calendar.MINUTE, 1) }
                    if (endC.timeInMillis > from.timeInMillis) best = earlierOf(best, endC)
                } else {
                    // 跨午夜：结束点落在「开始日 +1 天」的 end+1 分
                    val nextDay = day.clone() as Calendar
                    nextDay.add(Calendar.DAY_OF_YEAR, 1)
                    val endC = atTime(nextDay, s.endMinute).apply { add(Calendar.MINUTE, 1) }
                    if (endC.timeInMillis > from.timeInMillis) best = earlierOf(best, endC)
                }
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1)
        }
        return best
    }

    /** 以 day 的日期为基准，构造当天 minuteOfDay 时刻（清零秒/毫秒） */
    private fun atTime(day: Calendar, minuteOfDay: Int): Calendar {
        val c = day.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        c.set(Calendar.MINUTE, minuteOfDay % 60)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c
    }

    private fun earlierOf(a: Calendar?, b: Calendar): Calendar =
        if (a == null || a.timeInMillis <= b.timeInMillis) a ?: b else b
}
