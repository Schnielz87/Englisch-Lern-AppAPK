package de.englischlernen.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.englischlernen.app.EnglishApp
import de.englischlernen.app.MainActivity
import de.englischlernen.app.R
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Taegliche Lern-Erinnerung – nur wenn das Tagesziel noch nicht erreicht ist. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as EnglishApp).container
        val settings = container.settings.current()
        if (!settings.remindersEnabled) return Result.success()
        val today = container.database.dailyActivity().get(container.progress.today())
        if ((today?.xp ?: 0) >= settings.dailyGoalXp) return Result.success()
        Reminders.notify(applicationContext, settings.userName)
        return Result.success()
    }
}

object Reminders {
    private const val CHANNEL = "reminders"
    private const val WORK = "daily_reminder"

    fun schedule(context: Context, hour: Int) {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(0).withSecond(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK)

    fun notify(context: Context, name: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Lern-Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hello = if (name.isBlank()) "Zeit für Englisch!" else "$name, Zeit für Englisch!"
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(hello)
            .setContentText("Ein paar Minuten genügen, um deine Serie zu halten.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(1, n)
    }
}
