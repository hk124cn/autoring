package com.example.autoring.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val alarmVolumePercent: Int = 80,
    val ringerMode: String = "NORMAL",
    val vibration: Boolean = false,
    val priority: Int = 0,
    val isDefault: Boolean = false
)
