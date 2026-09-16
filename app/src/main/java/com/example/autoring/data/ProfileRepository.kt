package com.example.autoring.data

class ProfileRepository(private val db: AppDatabase) {
    fun observeProfiles() = db.dao().observeProfiles()
    fun observeSchedules() = db.dao().observeSchedules()

    suspend fun addProfile(profile: Profile) = db.dao().insertProfile(profile)
    suspend fun updateProfile(profile: Profile) = db.dao().updateProfile(profile)
    suspend fun deleteProfile(profile: Profile) = db.dao().deleteProfile(profile)

    suspend fun addSchedule(schedule: Schedule) = db.dao().insertSchedule(schedule)
    suspend fun updateSchedule(schedule: Schedule) = db.dao().updateSchedule(schedule)
    suspend fun deleteSchedule(schedule: Schedule) = db.dao().deleteSchedule(schedule)

    suspend fun getAllProfiles() = db.dao().getAllProfiles()
    suspend fun getAllSchedules() = db.dao().getAllSchedules()
    suspend fun getDefaultProfile() = db.dao().getDefaultProfile()

    suspend fun setDefaultProfile(id: Long) {
        db.dao().clearDefault()
        db.dao().setDefault(id)
    }
}
