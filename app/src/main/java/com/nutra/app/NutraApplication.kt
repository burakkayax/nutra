package com.nutra.app

import android.app.Application
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.notification.WaterReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class NutraApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Eagerly injected so DataStore starts reading the tiny preferences proto on IO in parallel with Activity launch.
    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    // Deferred lazily off Application.onCreate() so NotificationManager/AlarmManager Binder IPC stays off the critical startup path.
    @Inject
    lateinit var waterReminderScheduler: dagger.Lazy<WaterReminderScheduler>

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            val reminder = preferencesDataStore.getSnapshot().waterReminder
            if (reminder.enabled) {
                delay(800L)
                waterReminderScheduler.get().sync(reminder)
            }
        }
    }
}
