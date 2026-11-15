package com.tathkirati.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.car.app.notification.CarAppExtender
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import java.util.Locale

/**
 * يعرف شوكت التلفون اتصل بالسيارة:
 * 1) البلوتوث اتصل بجهاز اسمه يحتوي "Kia" (يتغير من الإعدادات)
 * 2) بعدها يتأكد من أندرويد أوتو نفسه إذا اشتغل، ويقرا القائمة
 */
object CarWatch {
    const val CHECK = "car_check"
    private const val THROTTLE = 15 * 60_000L // ما يعيد أكثر من مرة كل ربع ساعة
    private const val NOTIF_ID = 777

    /** 2 = أندرويد أوتو متصل، 1 = سيارة بنظام أندرويد، 0 = مو متصل، null = ما نعرف */
    fun carState(c: Context): Int? = try {
        c.contentResolver.query(
            Uri.parse("content://androidx.car.app.connection"),
            arrayOf("CarConnectionState"), null, null, null
        )?.use { cur ->
            if (cur.moveToFirst()) cur.getInt(cur.getColumnIndexOrThrow("CarConnectionState")) else null
        }
    } catch (_: Exception) {
        null
    }

    fun isCarDevice(c: Context, d: BluetoothDevice?): Boolean {
        if (d == null) return false
        val name = try { d.name } catch (_: SecurityException) { null } ?: return false
        val want = Prefs.carName(c).trim()
        return want.isNotEmpty() && name.contains(want, ignoreCase = true)
    }

    /** يفحص بعد شوية لأن أندرويد أوتو ياخذ وقت لحد ما يشتغل */
    fun scheduleCheck(c: Context, attempt: Int, delayMs: Long) {
        val i = Intent(c, CarCheckReceiver::class.java).setAction(CHECK).putExtra("attempt", attempt)
        val p = PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    /** يقرا القائمة (إشعار + صوت). force = تجاهل حد الربع ساعة */
    fun announce(c: Context, force: Boolean = false): Boolean {
        val text = Items.announcement(c) ?: return false
        val now = System.currentTimeMillis()
        if (!force && now - Prefs.lastAnnounce(c) < THROTTLE) return false
        Prefs.setLastAnnounce(c, now)
        showNotification(c, text)
        if (Prefs.speak(c)) {
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(Intent(c, AnnounceService::class.java).putExtra("text", text))
                else c.startService(Intent(c, AnnounceService::class.java).putExtra("text", text))
            } catch (_: Exception) {
                Speaker.speak(c, text) {}
            }
        }
        return true
    }

    private fun showNotification(c: Context, text: String) {
        Notifier.ensureChannel(c)
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val me = Person.Builder().setName("أنا").build()
        val sender = Person.Builder().setName("لا تنسى").setKey("tathkirati-list").build()
        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle("لا تنسى 🛒")
            .addMessage(text, System.currentTimeMillis(), sender)
        val car = CarAppExtender.Builder()
            .setContentTitle("لا تنسى 🛒")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notif)
            .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
            .build()
        val n = NotificationCompat.Builder(c, "reminders")
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("لا تنسى 🛒")
            .setContentText(text)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .extend(car)
            .build()
        try {
            NotificationManagerCompat.from(c).notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
        }
    }
}

/** البلوتوث اتصل بجهاز */
class CarBtReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Prefs.carEnabled(c)) return
        if (i.action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED &&
            i.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) != BluetoothProfile.STATE_CONNECTED
        ) return
        @Suppress("DEPRECATION")
        val d: BluetoothDevice? = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        if (CarWatch.isCarDevice(c, d)) CarWatch.scheduleCheck(c, 1, 15_000)
    }
}

/** يفحص إذا أندرويد أوتو اشتغل، وإذا بعد ينطيه وقت */
class CarCheckReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Prefs.carEnabled(c)) return
        val attempt = i.getIntExtra("attempt", 1)
        when (CarWatch.carState(c)) {
            0 -> {
                if (attempt < 6) CarWatch.scheduleCheck(c, attempt + 1, 15_000)
                else CarWatch.announce(c) // البلوتوث متصل بالكيا، نقرا حتى لو أوتو ما اشتغل
            }
            else -> CarWatch.announce(c)
        }
    }
}

/** يقرا النص بصوت (يطلع من سماعات السيارة) */
object Speaker {
    private var tts: TextToSpeech? = null

    fun speak(c: Context, text: String, onDone: () -> Unit) {
        val main = Handler(Looper.getMainLooper())
        val am = c.getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs).build()
        var finished = false
        val finish = {
            main.post {
                if (!finished) {
                    finished = true
                    try { am?.abandonAudioFocusRequest(focus) } catch (_: Exception) {}
                    tts?.shutdown(); tts = null
                    onDone()
                }
            }
        }
        tts?.shutdown()
        tts = TextToSpeech(c.applicationContext) { status ->
            val t = tts
            if (status != TextToSpeech.SUCCESS || t == null) finish() else {
            t.language = Locale("ar")
            t.setAudioAttributes(attrs)
            t.setSpeechRate(0.95f)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { finish() }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { finish() }
            })
            try { am?.requestAudioFocus(focus) } catch (_: Exception) {}
            // تأخير بسيط حتى السيارة تجهز الصوت
            main.postDelayed({ t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "list") }, 800)
            }
        }
        main.postDelayed({ finish() }, 60_000)
    }
}

/** خدمة قصيرة تقرا القائمة وتطفي */
class AnnounceService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ch = "announce"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(ch, "القراءة بالصوت", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n: Notification = NotificationCompat.Builder(this, ch)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("🔊 دا أقرالك القائمة…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(778, n)
        val text = intent?.getStringExtra("text")
        if (text.isNullOrBlank()) stopSelf()
        else Speaker.speak(this, text) { stopSelf() }
        return START_NOT_STICKY
    }
}
