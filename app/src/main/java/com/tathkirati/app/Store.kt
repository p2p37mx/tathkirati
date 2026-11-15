package com.tathkirati.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** repeat: 0 = مرة وحدة، 1 = يومي، 2 = أسبوعي */
data class Reminder(val id: Long, val title: String, val time: Long, val repeat: Int)

object Store {
    private const val PREFS = "reminders"
    private val main = Handler(Looper.getMainLooper())

    /** الشاشات (التلفون والسيارة) تسجل هنا حتى تتحدث فورًا لما يتغير شي */
    val listeners = mutableSetOf<() -> Unit>()

    fun all(c: Context): MutableList<Reminder> {
        val raw = c.getSharedPreferences(PREFS, 0).getString("list", "[]") ?: "[]"
        val out = mutableListOf<Reminder>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out += Reminder(o.getLong("id"), o.getString("title"), o.getLong("time"), o.optInt("repeat", 0))
            }
        } catch (_: Exception) {
        }
        out.sortBy { it.time }
        return out
    }

    fun get(c: Context, id: Long): Reminder? = all(c).find { it.id == id }

    private fun save(c: Context, list: List<Reminder>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("time", it.time).put("repeat", it.repeat))
        }
        c.getSharedPreferences(PREFS, 0).edit().putString("list", a.toString()).commit()
        main.post { listeners.toList().forEach { l -> l() } }
    }

    fun put(c: Context, r: Reminder) {
        val list = all(c).filter { it.id != r.id }.toMutableList()
        list += r
        save(c, list)
        Scheduler.schedule(c, r)
    }

    fun remove(c: Context, id: Long) {
        save(c, all(c).filter { it.id != id })
        Scheduler.cancel(c, id)
    }

    fun newId(): Long = System.currentTimeMillis()
}

object Scheduler {
    const val DAY = 24L * 60 * 60 * 1000

    private fun pending(c: Context, id: Long): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.FIRE)
            .setData(Uri.parse("tathkirati://fire/$id"))
            .putExtra("id", id)
        return PendingIntent.getBroadcast(
            c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(c: Context, r: Reminder) {
        if (r.time <= System.currentTimeMillis()) return
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        val p = pending(c, r.id)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.time, p)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.time, p)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.time, p)
        }
    }

    fun cancel(c: Context, id: Long) {
        c.getSystemService(AlarmManager::class.java)?.cancel(pending(c, id))
    }

    /** الموعد الجاي للتذكير المتكرر */
    fun next(r: Reminder): Long {
        val step = if (r.repeat == 2) 7 * DAY else DAY
        var t = r.time
        val now = System.currentTimeMillis()
        while (t <= now) t += step
        return t
    }

    /** بعد إعادة تشغيل التلفون أو تحديث التطبيق */
    fun rescheduleAll(c: Context) {
        Store.all(c).forEach {
            if (it.repeat != 0 && it.time <= System.currentTimeMillis()) Store.put(c, it.copy(time = next(it)))
            else schedule(c, it)
        }
    }
}

/** أوامر مشتركة بين الإشعار وشاشة السيارة وشاشة التلفون */
object Actions {
    const val SNOOZE_MIN = 10

    fun done(c: Context, id: Long) {
        Notifier.cancel(c, id)
        val r = Store.get(c, id) ?: return
        if (r.repeat == 0) Store.remove(c, id)
    }

    fun snooze(c: Context, id: Long, fallbackTitle: String?) {
        Notifier.cancel(c, id)
        val r = Store.get(c, id)
        val title = r?.title ?: fallbackTitle ?: "تذكير"
        val t = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
        if (r != null && r.repeat == 0) Store.put(c, r.copy(time = t))
        else Store.put(c, Reminder(Store.newId(), title, t, 0))
    }

    fun quick(c: Context, minutes: Int, title: String) {
        Store.put(c, Reminder(Store.newId(), title, System.currentTimeMillis() + minutes * 60_000L, 0))
    }
}

object Fmt {
    private val ar = Locale("ar")

    fun repeatLabel(r: Int) = when (r) {
        1 -> "يومي"
        2 -> "أسبوعي"
        else -> "مرة وحدة"
    }

    fun time(t: Long): String = SimpleDateFormat("h:mm a", ar).format(Date(t))

    fun whenText(r: Reminder): String {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { timeInMillis = r.time }
        val sameDay = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
        now.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
        val day = when {
            sameDay -> "اليوم"
            tomorrow -> "باچر"
            else -> SimpleDateFormat("EEEE d/M", ar).format(Date(r.time))
        }
        val past = if (r.time <= System.currentTimeMillis()) "  • فات وقته" else ""
        val rep = if (r.repeat != 0) "  • ${repeatLabel(r.repeat)}" else ""
        return "$day ${time(r.time)}$rep$past"
    }
}
