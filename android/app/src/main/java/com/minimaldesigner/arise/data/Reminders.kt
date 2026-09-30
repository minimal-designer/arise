package com.minimaldesigner.arise.data

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
import com.minimaldesigner.arise.AriseApp
import com.minimaldesigner.arise.MainActivity
import com.minimaldesigner.arise.R
import com.minimaldesigner.arise.core.Pause
import com.minimaldesigner.arise.core.eveningNudge
import com.minimaldesigner.arise.core.nextReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The evening reminder: one inexact alarm a day (a 10-minute window, so no exact-alarm
 * permission), which posts a notification only if today's tasks are still open. None
 * during a break: then one alarm on the morning it ends says welcome back instead.
 */
object Reminders {
    private const val CHANNEL = "evening"
    private const val PAUSE_CHANNEL = "pause"
    private const val NOTIFICATION_ID = 1
    private const val PAUSE_NOTIFICATION_ID = 2
    const val ACTION_FIRE = "com.minimaldesigner.arise.REMINDER"
    const val ACTION_PAUSE_END = "com.minimaldesigner.arise.PAUSE_END"

    /** When the break-over notification fires: the morning of the day back. */
    private val PAUSE_END_AT: LocalTime = LocalTime.of(9, 0)

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Arms the next alarm from [settings], or cancels it when the reminder is off. */
    fun schedule(context: Context, settings: Settings) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        if (!settings.reminderOn) {
            am.cancel(pi)
            return
        }
        val at = nextReminder(LocalDateTime.now(), settings.reminderAt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60 * 1000L, pi)
    }

    private fun pauseIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_PAUSE_END),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Arms the break-over alarm for 9 am on [pause]'s day back, or cancels it (no break, or that morning has passed). */
    fun schedulePauseEnd(context: Context, pause: Pause?) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pauseIntent(context)
        val at = pause?.untilDate?.atTime(PAUSE_END_AT)
        if (at == null || !at.isAfter(LocalDateTime.now())) {
            am.cancel(pi)
            return
        }
        am.setWindow(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 10 * 60 * 1000L, pi)
    }

    /** Posts the reminder if anything is still open today, there's no break, and notifications are allowed. */
    suspend fun fire(context: Context) {
        val app = context.applicationContext as AriseApp
        val run = app.challenges.run.first()
        val days = app.challenges.liveDays.first()
        val paused = app.challenges.meta.first().pause != null
        val nudge = eveningNudge(run, days, LocalDate.now(), paused) ?: return
        post(context, CHANNEL, "Evening reminder", "Once a day, if today's tasks aren't all done", NOTIFICATION_ID, nudge.title, nudge.text)
    }

    /** The break is over: says welcome back, if the break wasn't already ended in the app. */
    suspend fun firePauseEnd(context: Context) {
        val app = context.applicationContext as AriseApp
        val pause = app.challenges.meta.first().pause ?: return
        if (app.challenges.run.first() == null || LocalDate.now().isBefore(pause.untilDate)) return
        post(
            context, PAUSE_CHANNEL, "Break over", "When a pause you set ends", PAUSE_NOTIFICATION_ID,
            "Your ${pause.phrase} is over",
            "Welcome back. Your challenge restarts at Day 1: open ARISE to get back on track.",
        )
    }

    private fun post(context: Context, channel: String, name: String, about: String, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(channel, name, NotificationManager.IMPORTANCE_DEFAULT).apply { description = about },
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFE8561C.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and here: nothing to do.
        }
    }
}

/** Fires the reminders, and re-arms the alarms after one fires, a reboot, an update or a clock change. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as AriseApp
                val settings = app.prefs.settings.first()
                if (intent.action == Reminders.ACTION_FIRE && settings.reminderOn) Reminders.fire(context)
                if (intent.action == Reminders.ACTION_PAUSE_END) Reminders.firePauseEnd(context)
                Reminders.schedule(context, settings)
                Reminders.schedulePauseEnd(context, app.challenges.meta.first().pause)
            } finally {
                pending.finish()
            }
        }
    }
}
