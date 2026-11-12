package com.yash.upsc400v2.timer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yash.upsc400v2.MainActivity
import com.yash.upsc400v2.data.Store
import com.yash.upsc400v2.engine.TimerMath
import java.util.Calendar

object Notifier {
    const val CH_TIMER = "timer"
    const val CH_ALERT = "alert"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH_TIMER, "Session timer", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Alerts", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(ctx, "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED
    }

    fun post(ctx: Context, id: Int, n: android.app.Notification) {
        if (!canPost(ctx)) return
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (e: SecurityException) {
            // permission revoked while running
        }
    }
}

class TimerController(private val ctx: Context, private val store: Store) {

    private fun alarmIntent(): PendingIntent {
        val i = Intent(ctx, BuzzReceiver::class.java)
        return PendingIntent.getBroadcast(ctx, 11, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun start(sessionId: Long) {
        val now = System.currentTimeMillis()
        val cur = store.timer()
        if (cur != null && cur.running && cur.sessionId != sessionId) pause()
        val session = store.session(sessionId) ?: return
        val logged = store.loggedMs(sessionId)
        store.timerStart(sessionId, now)
        Notifier.ensureChannels(ctx)
        val buzz = TimerMath.buzzAt(session.plannedMin.toLong(), logged, now)
        if (buzz > 0) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, buzz, alarmIntent())
        }
        if (store.getSetting("timer_notif", "1") == "1") {
            val open = PendingIntent.getActivity(
                ctx, 12, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, Notifier.CH_TIMER)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(session.subject)
                .setContentText("Planned " + session.plannedMin + " min")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(now - logged)
                .setContentIntent(open)
                .build()
            Notifier.post(ctx, 1, n)
        }
    }

    fun pause() {
        store.timerPause(System.currentTimeMillis())
        cancelAll()
    }

    fun cancelAll() {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent())
        NotificationManagerCompat.from(ctx).cancel(1)
    }

    // ---- daily reminder

    private fun reminderIntent(): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
        return PendingIntent.getBroadcast(ctx, 13, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun scheduleReminder(hour: Int, minute: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, reminderIntent())
    }

    fun cancelReminder() {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(reminderIntent())
    }
}

class BuzzReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Notifier.ensureChannels(context)
        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE))
        val n = NotificationCompat.Builder(context, Notifier.CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Planned time reached")
            .setContentText("Finish up or keep going.")
            .setAutoCancel(true)
            .build()
        Notifier.post(context, 2, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Notifier.ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, 14, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, Notifier.CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Time to study")
            .setContentText("Your next session is waiting.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        Notifier.post(context, 3, n)
    }
}
