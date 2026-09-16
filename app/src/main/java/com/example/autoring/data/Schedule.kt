package com.example.autoring.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startMinute: Int = 0,
    val endMinute: Int = 0,
    val daysOfWeek: Int = 0,
    val enabled: Boolean = true
)
