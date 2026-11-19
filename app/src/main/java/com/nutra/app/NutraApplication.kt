package com.nutra.app

import android.app.Application
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.notification.WaterReminderScheduler
import com.nutra.app.domain.repository.NutritionRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NutraApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Eagerly injected so DataStore & Room warm up on IO threads in parallel with Activity launch.
    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var nutritionRepository: NutritionRepository

    @Inject
    lateinit var waterReminderScheduler: WaterReminderScheduler

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val reminder = preferencesDataStore.getSnapshot().waterReminder
            waterReminderScheduler.ensureNotificationChannel()
            if (reminder.enabled) {
                waterReminderScheduler.sync(reminder)
            }
        }
    }
}
