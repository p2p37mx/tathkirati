package com.tathkirati.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.car.app.notification.CarAppExtender
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput

object Notifier {
    private const val CHANNEL = "reminders"

    fun notifId(id: Long): Int = (id % Int.MAX_VALUE).toInt()

    fun ensureChannel(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "التذكيرات", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيهات التذكيرات بالتلفون والسيارة"
                enableVibration(true)
            }
            c.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun action(c: Context, act: String, r: Reminder, mutable: Boolean = false): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java)
            .setAction(act)
            .setData(Uri.parse("tathkirati://$act/${r.id}"))
            .putExtra("id", r.id)
            .putExtra("title", r.title)
        val flag = if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE
        else if (mutable) 0 else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or flag)
    }

    fun show(c: Context, r: Reminder) {
        ensureChannel(c)
        val donePi = action(c, ReminderReceiver.DONE, r)
        val snoozePi = action(c, ReminderReceiver.SNOOZE, r)
        val replyPi = action(c, ReminderReceiver.REPLY, r, mutable = true)

        val openApp = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

        // شكل "رسالة" حتى أندرويد أوتو يعرضه على شاشة السيارة ويقراه بالصوت
        val me = Person.Builder().setName("أنا").build()
        val sender = Person.Builder().setName("تذكير").setKey("tathkirati").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("تذكيراتي")
            .addMessage(r.title, System.currentTimeMillis(), sender)

        val doneAction = NotificationCompat.Action.Builder(R.drawable.ic_check, "تم ✓", donePi)
            .setShowsUserInterface(false)
            .build()
        val markRead = NotificationCompat.Action.Builder(R.drawable.ic_check, "تم", donePi)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
        val reply = NotificationCompat.Action.Builder(R.drawable.ic_snooze, "أجّل ${Actions.SNOOZE_MIN} دقايق", replyPi)
            .addRemoteInput(RemoteInput.Builder("reply").setLabel("أجّل").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        val car = CarAppExtender.Builder()
            .setContentTitle("⏰ تذكير")
            .setContentText(r.title)
            .setSmallIcon(R.drawable.ic_notif)
            .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
            .addAction(R.drawable.ic_check, "تم ✓", donePi)
            .addAction(R.drawable.ic_snooze, "أجّل ${Actions.SNOOZE_MIN} د", snoozePi)
            .build()

        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("⏰ تذكير")
            .setContentText(r.title)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .addAction(doneAction)
            .addAction(reply)
            .addInvisibleAction(markRead)
            .extend(car)
            .build()

        try {
            NotificationManagerCompat.from(c).notify(notifId(r.id), n)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(c: Context, id: Long) {
        NotificationManagerCompat.from(c).cancel(notifId(id))
    }
}
