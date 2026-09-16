package com.example.autoring.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.autoring.data.Profile

class AudioProfileManager(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun hasNotificationPolicyAccess(): Boolean =
        notificationManager.isNotificationPolicyAccessGranted

    fun hasVibrator(): Boolean = vibrator?.hasVibrator() == true

    /**
     * 应用一个情景模式：
     * - 闹钟音流：按百分比缩放到最大值，独立设置（不受勿扰影响）
     * - 来电/通知模式：需要勿扰权限才能改 RINGER_MODE
     * - 震动：仅在模式为 VIBRATE/SILENT 且 profile.vibration=true 时触发短震动提示
     *   （来电震动本身由 RINGER_MODE_VIBRATE 控制；这里只是给用户一个切换反馈）
     */
    /**
     * 应用一个情景模式。
     * @param feedbackVibrate 是否给出一次「切换确认」短震动。
     *        手动点「应用」时传 true；但**定时/开机自动切换必须传 false**，
     *        否则深夜切到「睡眠」模式时手机会震动，反而把人吵醒。
     */
    fun applyProfile(profile: Profile, feedbackVibrate: Boolean = true) {
        // 1. 闹钟音流（独立音流，无需勿扰权限）
        val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val alarmVol = (profile.alarmVolumePercent * maxAlarm / 100).coerceIn(0, maxAlarm)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, alarmVol, 0)

        // 2. 来电/通知模式（需勿扰权限）
        if (notificationManager.isNotificationPolicyAccessGranted) {
            when (profile.ringerMode) {
                "RING_VIBRATE" -> {
                    // 响铃 + 震动：响铃模式 + 打开「响铃时震动」「通知震动」
                    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL)
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    setVibrate(AudioManager.VIBRATE_TYPE_RINGER, true)
                    setVibrate(AudioManager.VIBRATE_TYPE_NOTIFICATION, true)
                }
                "VIBRATE" -> {
                    if (audioManager.ringerMode != AudioManager.RINGER_MODE_VIBRATE)
                        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    setVibrate(AudioManager.VIBRATE_TYPE_RINGER, true)
                }
                "SILENT" -> {
                    if (audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT)
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                else -> { // NORMAL：纯响铃，关掉震动
                    if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL)
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    setVibrate(AudioManager.VIBRATE_TYPE_RINGER, false)
                    setVibrate(AudioManager.VIBRATE_TYPE_NOTIFICATION, false)
                }
            }
        }

        // 3. 震动反馈：仅手动应用且模式开启「震动反馈」时，给一次短震动确认；
        //    自动切换（定时/开机）传 false，避免深夜切到睡眠模式时把人震醒
        if (feedbackVibrate && profile.vibration) {
            triggerVibration()
        }
    }

    private fun setVibrate(type: Int, on: Boolean) {
        try {
            audioManager.setVibrateSetting(
                type,
                if (on) AudioManager.VIBRATE_SETTING_ON else AudioManager.VIBRATE_SETTING_OFF
            )
        } catch (e: Exception) {
            Log.w("AutoRing", "设置震动开关失败 type=$type", e)
        }
    }

    private fun triggerVibration() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(150)
        }
    }

    /** 供「测试震动」按钮调用，让用户确认本机震动正常 */
    fun testVibration() = triggerVibration()

    /**
     * 读取当前系统音量状态，返回结构化数据供界面渲染。
     * 所有读操作都很快（系统服务内存查询），不会阻塞。
     */
    fun currentStatus(): SystemAudioStatus {
        val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        val alarmVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        val ringVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        val ringMode = when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            else -> "NORMAL"
        }
        return SystemAudioStatus(
            alarmVolume = alarmVol,
            alarmMax = maxAlarm,
            ringVolume = ringVol,
            ringMax = maxRing,
            ringerMode = ringMode,
            policyAccess = hasNotificationPolicyAccess()
        )
    }
}

/** 系统当前音频状态的结构化快照 */
data class SystemAudioStatus(
    val alarmVolume: Int = 0,
    val alarmMax: Int = 0,
    val ringVolume: Int = 0,
    val ringMax: Int = 0,
    val ringerMode: String = "NORMAL",
    val policyAccess: Boolean = false
) {
    val ringerModeLabel: String
        get() = when (ringerMode) {
            "VIBRATE" -> "震动"
            "SILENT" -> "静音"
            else -> "响铃"
        }
    val alarmPercent: Int
        get() = if (alarmMax > 0) (alarmVolume * 100 / alarmMax) else 0
}
