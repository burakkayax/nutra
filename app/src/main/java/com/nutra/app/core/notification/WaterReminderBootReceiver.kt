package com.nutra.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nutra.app.core.datastore.UserPreferencesDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class WaterReminderBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesDataStore: UserPreferencesDataStore

    @Inject
    lateinit var waterReminderScheduler: WaterReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return

        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                withTimeout(RECEIVER_TIMEOUT_MS) {
                    try {
                        val settings = preferencesDataStore.getSnapshot().waterReminder
                        waterReminderScheduler.sync(settings)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Ignore transient boot sync errors
                    }
                }
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private companion object {
        const val RECEIVER_TIMEOUT_MS = 8_000L
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
