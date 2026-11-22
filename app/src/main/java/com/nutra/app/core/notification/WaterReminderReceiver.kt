package com.nutra.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nutra.app.core.datastore.UserPreferencesDataStore
import com.nutra.app.domain.repository.NutritionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class WaterReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var nutritionRepository: NutritionRepository

    @Inject
    lateinit var waterReminderScheduler: WaterReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MS) {
                    try {
                        when (action) {
                            WaterReminderScheduler.ACTION_REMINDER_TRIGGER -> handleReminderTrigger()
                            WaterReminderScheduler.ACTION_LOG_WATER_QUICK -> {
                                val deltaMl = intent.getIntExtra(WaterReminderScheduler.EXTRA_WATER_ML, 200)
                                    .coerceIn(50, 1000)
                                nutritionRepository.addWaterMl(LocalDate.now().toString(), deltaMl)
                                waterReminderScheduler.dismissNotification()
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Ignore transient broadcast execution failures; alarm chain is safeguarded in finally
                    }
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    internal suspend fun handleReminderTrigger() {
        val profile = preferencesDataStore.getSnapshot()
        val settings = profile.waterReminder
        if (!settings.enabled) {
            waterReminderScheduler.cancel()
            return
        }

        // Always reschedule the next reminder slot even if DB/notification check throws transiently
        try {
            val nowTime = LocalTime.now()
            if (WaterReminderScheduleCalculator.isWithinActiveWindow(nowTime, settings)) {
                val todayWaterMl = nutritionRepository.getWaterMl(LocalDate.now().toString())
                val goalWaterMl = profile.goals.waterMl
                if (!settings.stopWhenGoalReached || todayWaterMl < goalWaterMl) {
                    waterReminderScheduler.showReminderNotification(
                        todayWaterMl = todayWaterMl,
                        goalWaterMl = goalWaterMl,
                    )
                }
            }
        } finally {
            waterReminderScheduler.sync(settings, minLeadSeconds = 60L)
        }
    }

    private companion object {
        const val RECEIVER_TIMEOUT_MS = 8_000L
    }
}
