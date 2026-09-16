package com.example.autoring.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // goAsync 让系统在本广播处理完成前保持进程存活，
        // 否则 ColorOS 等激进杀后台会在这次 IO 协程跑完前回收进程，导致切换/重排丢失。
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ProfileScheduler.applyCurrent(context)
                ProfileScheduler.scheduleNext(context)
            } finally {
                pending.finish()
            }
        }
    }
}
