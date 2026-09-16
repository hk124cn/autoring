package com.example.autoring

import android.app.Application
import com.example.autoring.data.AppDatabase

class AutoRingApplication : Application() {
    val database by lazy { AppDatabase.get(this) }
}
