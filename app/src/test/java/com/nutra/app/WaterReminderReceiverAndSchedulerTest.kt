package com.nutra.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nutra.app.core.database.NutraDatabase
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.core.notification.WaterReminderReceiver
import com.nutra.app.core.notification.WaterReminderScheduleCalculator
import com.nutra.app.core.notification.WaterReminderScheduler
import com.nutra.app.domain.model.UserProfile
import com.nutra.app.domain.model.WaterReminderSettings
import com.nutra.app.domain.repository.NutritionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class WaterReminderReceiverAndSchedulerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var app: Application
    private lateinit var database: NutraDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var preferences: UserPreferencesDataStore

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        app = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(app, NutraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val prefsFile = File(tempFolder.newFolder("wr_ds_${UUID.randomUUID()}"), "nutra_wr.preferences_pb")
        val backing = PreferenceDataStoreFactory.create(scope = dataStoreScope) { prefsFile }
        preferences = UserPreferencesDataStore(app, backing)
    }

    @After
    fun tearDown() {
        database.close()
        dataStoreScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun scheduler_detectsMutedNotificationChannelAndTargetsChannelSettingsIntent() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val nm = app.getSystemService(NotificationManager::class.java)!!
        val scheduler = WaterReminderScheduler(app)
        scheduler.ensureNotificationChannel()

        assertTrue("Fresh default channel should report permission granted", scheduler.hasNotificationPermission())
        assertFalse("Fresh default channel should not report muted", scheduler.isChannelMuted())

        // Simulate user muting the Nutra water reminder notification channel in Android settings
        nm.createNotificationChannel(
            NotificationChannel(
                WaterReminderScheduler.CHANNEL_ID,
                "Su hatırlatıcı",
                NotificationManager.IMPORTANCE_NONE,
            ),
        )

        assertFalse("Muted channel must report hasNotificationPermission=false", scheduler.hasNotificationPermission())
        assertTrue("Muted channel must report isChannelMuted=true", scheduler.isChannelMuted())

        val settingsIntent = scheduler.buildNotificationSettingsIntent()
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, settingsIntent.action)
        assertEquals(WaterReminderScheduler.CHANNEL_ID, settingsIntent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test
    fun scheduler_syncSchedulesApproximateIdleAlarmAndCancelRemovesIt() {
        val scheduler = WaterReminderScheduler(app)
        val alarmManager = app.getSystemService(AlarmManager::class.java)!!
        val shadowAlarm = shadowOf(alarmManager)

        scheduler.sync(
            WaterReminderSettings(
                enabled = true,
                intervalMinutes = 90,
                startHour = 8,
                startMinute = 0,
                endHour = 22,
                endMinute = 0,
            ),
        )
        assertEquals("Expected exactly 1 approximate scheduled alarm", 1, shadowAlarm.scheduledAlarms.size)
        @Suppress("DEPRECATION")
        val scheduledType = shadowAlarm.scheduledAlarms.first().type
        assertEquals(AlarmManager.RTC_WAKEUP, scheduledType)

        scheduler.sync(WaterReminderSettings(enabled = false))
        assertEquals("Disabling reminders must cancel the scheduled alarm", 0, shadowAlarm.scheduledAlarms.size)
    }

    @Test
    fun receiverHandleTrigger_alwaysReschedulesInFinallyOnDbErrorAndRethrowsCancellation() = runBlocking {
        // Ensure active window spans all day so handleReminderTrigger always queries DB
        val allDaySettings = WaterReminderSettings(
            enabled = true,
            intervalMinutes = 60,
            startHour = 0,
            startMinute = 0,
            endHour = 23,
            endMinute = 59,
            stopWhenGoalReached = true,
        )
        preferences.saveOnboardingProfile(UserProfile(onboardingCompleted = true))
        preferences.updateWaterReminder(allDaySettings)

        val syncCalls = AtomicInteger(0)
        val spyScheduler = object : WaterReminderScheduler(app) {
            override fun sync(settings: WaterReminderSettings, minLeadSeconds: Long) {
                syncCalls.incrementAndGet()
            }

            override fun cancel() = Unit
            override fun showReminderNotification(todayWaterMl: Int, goalWaterMl: Int) = Unit
        }

        // Case 1: Transient DB error -> exception propagates to onReceive catch, BUT finally MUST still reschedule!
        val failingRepo = object : NutritionRepository(app, database, database.nutraDao(), preferences) {
            override suspend fun getWaterMl(dateIso: String): Int {
                throw IllegalStateException("Transient SQLite busy")
            }
        }
        val receiver = WaterReminderReceiver().apply {
            preferencesDataStore = preferences
            nutritionRepository = failingRepo
            waterReminderScheduler = spyScheduler
        }
        try {
            receiver.handleReminderTrigger()
            fail("Expected IllegalStateException to bubble out of handleReminderTrigger")
        } catch (_: IllegalStateException) {
            // Expected
        }
        assertEquals("finally block MUST reschedule next reminder even on DB failure", 1, syncCalls.get())

        // Case 2: Coroutine CancellationException must be rethrown (never swallowed) AND still execute finally!
        val cancellingRepo = object : NutritionRepository(app, database, database.nutraDao(), preferences) {
            override suspend fun getWaterMl(dateIso: String): Int {
                throw CancellationException("Receiver timed out")
            }
        }
        receiver.nutritionRepository = cancellingRepo
        try {
            receiver.handleReminderTrigger()
            fail("Expected CancellationException to be rethrown")
        } catch (_: CancellationException) {
            // Expected
        }
        assertEquals("finally block must run on cancellation path as well", 2, syncCalls.get())
    }

    @Test
    fun scheduleCalculator_honors45MinDozeTrailingGraceAndApproximateTildeLabel() {
        val settings = WaterReminderSettings(
            enabled = true,
            intervalMinutes = 90,
            startHour = 9,
            startMinute = 0,
            endHour = 21,
            endMinute = 0,
        )
        assertTrue(
            "Doze-delayed alarm at 21:45 (within 45-min grace) must still be accepted",
            WaterReminderScheduleCalculator.isWithinActiveWindow(LocalTime.of(21, 45), settings),
        )
        assertFalse(
            "Alarm at 21:46 (past 45-min grace) must be outside active window",
            WaterReminderScheduleCalculator.isWithinActiveWindow(LocalTime.of(21, 46), settings),
        )
        val now = java.time.LocalDateTime.of(2026, 11, 21, 9, 15)
        val next = java.time.LocalDateTime.of(2026, 11, 21, 10, 30)
        assertEquals("Bugün ~10:30", WaterReminderScheduleCalculator.formatNextTriggerLabel(now, next))
        assertTrue(
            "Profile summary must communicate approximate interval (~)",
            WaterReminderScheduleCalculator.formatProfileSummary(settings).contains("~"),
        )
    }
}
