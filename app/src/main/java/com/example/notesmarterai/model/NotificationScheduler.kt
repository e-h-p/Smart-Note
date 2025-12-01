package com.example.notesmarterai.model

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.notesmarterai.receiver.ReminderReceiver
import java.text.SimpleDateFormat
import java.util.*

object NotificationScheduler {

    private const val CHANNEL_ID = "channel_reminders"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Note Reminders"
            val descriptionText = "Notifications for scheduled notes"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleReminder(context: Context, note: Note) {
        if (note.reminderTime.equals("None", ignoreCase = true) || note.reminderTime.isEmpty()) {
            cancelReminder(context, note)
            return
        }

        val timeInMillis = parseDateToMillis(note.reminderTime)

        // Debug log to check if parsing works
        Log.d("Scheduler", "Scheduling for: ${note.reminderTime} -> Millis: $timeInMillis")

        if (timeInMillis <= System.currentTimeMillis()) {
            Log.d("Scheduler", "Time is in the past, skipping alarm.")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("NOTE_TITLE", note.title)
            putExtra("NOTE_ID_HASH", note.id.hashCode())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            note.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                } else {
                    // Fallback if permission lost
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelReminder(context: Context, note: Note) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            note.id.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun parseDateToMillis(dateString: String): Long {
        return try {
            // CRITICAL FIX: Pattern must match "25/11/2025 14:30" (24h format)
            // Old pattern was "dd/MM/yyyy HH:mm a" which failed because 'AM/PM' is gone
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            format.isLenient = false // Strict parsing
            val date = format.parse(dateString)
            date?.time ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Scheduler", "Date Parsing Failed for: $dateString")
            0L
        }
    }
}