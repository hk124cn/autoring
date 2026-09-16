package com.example.autoring.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileScheduleDao {
    @Query("SELECT * FROM profiles ORDER BY name ASC")
    fun observeProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM schedules ORDER BY startMinute ASC")
    fun observeSchedules(): Flow<List<Schedule>>

    @Query("SELECT * FROM profiles")
    suspend fun getAllProfiles(): List<Profile>

    @Query("SELECT * FROM schedules")
    suspend fun getAllSchedules(): List<Schedule>

    @Query("SELECT * FROM profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): Profile?

    @Query("UPDATE profiles SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE profiles SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)

    @Insert
    suspend fun insertProfile(profile: Profile): Long

    @Update
    suspend fun updateProfile(profile: Profile)

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Insert
    suspend fun insertSchedule(schedule: Schedule): Long

    @Update
    suspend fun updateSchedule(schedule: Schedule)

    @Delete
    suspend fun deleteSchedule(schedule: Schedule)
}
