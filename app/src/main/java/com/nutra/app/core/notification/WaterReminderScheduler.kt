package com.nutra.app.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nutra.app.MainActivity
import com.nutra.app.R
import com.nutra.app.core.designsystem.NutraFormatters
import com.nutra.app.domain.model.WaterReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class WaterReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "nutra_water_reminders"
        const val NOTIFICATION_ID = 4201
        const val ACTION_REMINDER_TRIGGER = "com.nutra.app.action.WATER_REMINDER_TRIGGER"
        const val ACTION_LOG_WATER_QUICK = "com.nutra.app.action.WATER_REMINDER_LOG_QUICK"
        const val EXTRA_WATER_ML = "extra_water_ml"
        private const val REQUEST_CODE_ALARM = 4201
        private const val REQUEST_CODE_OPEN_APP = 4202
        private const val REQUEST_CODE_QUICK_ADD = 4203
        private const val WATER_ACCENT_ARGB = 0xFF2F9BEF.toInt()
    }

    @Volatile
    private var channelEnsured = false

    fun ensureNotificationChannel() {
        if (channelEnsured) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Su hatırlatıcı",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Gün içinde düzenli su içmen için hatırlatma bildirimleri"
            }
            manager.createNotificationChannel(channel)
        }
        channelEnsured = true
    }

    fun hasNotificationPermission(): Boolean {
        ensureNotificationChannel()
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun isChannelMuted(): Boolean {
        ensureNotificationChannel()
        val postGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!postGranted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID)
        return channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun buildNotificationSettingsIntent(): Intent {
        ensureNotificationChannel()
        return if (isChannelMuted()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
    }

    open fun sync(settings: WaterReminderSettings, minLeadSeconds: Long = 5L) {
        ensureNotificationChannel()
        val safe = WaterReminderScheduleCalculator.sanitize(settings)
        if (!safe.enabled) {
            cancel()
            return
        }

        val nextTrigger = WaterReminderScheduleCalculator.computeNextTriggerDateTime(
            now = LocalDateTime.now(),
            settings = safe,
            minLeadSeconds = minLeadSeconds,
        ) ?: run {
            cancel()
            return
        }

        val triggerAtMillis = nextTrigger
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildTriggerPendingIntent()
        alarmManager.cancel(pendingIntent)

        // Approximate Doze-friendly alarm without requiring special exact-alarm Play Store permissions
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent,
            )
        }
    }

    open fun cancel() {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager?.cancel(buildTriggerPendingIntent())
        dismissNotification()
    }

    fun dismissNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    open fun showReminderNotification(todayWaterMl: Int, goalWaterMl: Int) {
        if (!hasNotificationPermission()) return
        ensureNotificationChannel()

        val safeGoal = goalWaterMl.coerceAtLeast(1)
        val remainingMl = (safeGoal - todayWaterMl).coerceAtLeast(0)
        val bodyText = if (remainingMl > 0) {
            "Günlük hedefine ${NutraFormatters.formatInt(remainingMl)} ml kaldı (${NutraFormatters.formatInt(todayWaterMl)} / ${NutraFormatters.formatInt(safeGoal)} ml)."
        } else {
            "Günlük su hedefini tamamladın. Bir bardak su ile tazelenebilirsin."
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val quickAddIntent = Intent(context, WaterReminderReceiver::class.java).apply {
            action = ACTION_LOG_WATER_QUICK
            putExtra(EXTRA_WATER_ML, 200)
        }
        val quickAddPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_QUICK_ADD,
            quickAddIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop_rounded)
            .setColor(WATER_ACCENT_ARGB)
            .setContentTitle("Su içme zamanı")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "+200 ml ekle", quickAddPendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildTriggerPendingIntent(): PendingIntent {
        val intent = Intent(context, WaterReminderReceiver::class.java).apply {
            action = ACTION_REMINDER_TRIGGER
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
