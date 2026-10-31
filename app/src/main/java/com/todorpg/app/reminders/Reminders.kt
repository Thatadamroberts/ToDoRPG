package com.todorpg.app.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.todorpg.app.MainActivity
import com.todorpg.app.R
import com.todorpg.app.data.GameStore
import com.todorpg.app.data.Settings
import com.todorpg.app.data.TaskType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Schedules a once-a-day reminder about unfinished daily quests. */
object Reminders {
    private const val CHANNEL = "daily_reminder"
    private const val REQUEST = 1001

    fun saveFile(context: Context) = GameStore.saveFile(context)

    private fun load(context: Context) = GameStore.run {
        init(context)
        value
    }

    fun apply(context: Context, settings: Settings) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        alarm.cancel(pending)
        if (!settings.reminders) return

        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(settings.reminderHour, settings.reminderMinute)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact is fine for a reminder and needs no special permission.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily quest reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun notifyIfNeeded(context: Context) {
        val state = load(context)
        if (!state.settings.reminders) return
        val today = LocalDate.now().toEpochDay()
        val left = state.tasks.filter { it.type == TaskType.DAILY && !it.isDone(today) }
        if (left.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (left.size == 1) "1 daily quest remains!" else "${left.size} daily quests remain!"
        val body = "The ${state.monster.name} will strike at dawn. " +
            left.take(3).joinToString(", ") { it.title } + if (left.size > 3) "…" else ""
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(REQUEST, notification)
    }
}

/** Fires the reminder and re-arms it for tomorrow; also re-arms after a reboot. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = GameStore.run {
            init(context)
            value
        }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Reminders.notifyIfNeeded(context)
        }
        Reminders.apply(context, state.settings)
    }
}
