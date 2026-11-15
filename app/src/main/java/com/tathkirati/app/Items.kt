package com.tathkirati.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/** غرض بقائمة «لا تنسى» */
data class Item(val id: Long, val text: String, val done: Boolean)

object Items {
    private const val PREFS = "items"
    private val main = Handler(Looper.getMainLooper())

    fun all(c: Context): List<Item> {
        val raw = c.getSharedPreferences(PREFS, 0).getString("list", "[]") ?: "[]"
        val out = mutableListOf<Item>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out += Item(o.getLong("id"), o.getString("text"), o.optBoolean("done", false))
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun pending(c: Context) = all(c).filter { !it.done }

    private fun save(c: Context, list: List<Item>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
        c.getSharedPreferences(PREFS, 0).edit().putString("list", a.toString()).commit()
        main.post { Store.listeners.toList().forEach { l -> l() } }
    }

    /** يقبل أكثر من غرض مفصولين بفاصلة أو سطر جديد */
    fun add(c: Context, raw: String): Int {
        val parts = raw.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return 0
        var id = System.currentTimeMillis()
        val list = all(c).toMutableList()
        parts.forEach { list += Item(id++, it, false) }
        save(c, list)
        return parts.size
    }

    fun toggle(c: Context, id: Long) = save(c, all(c).map { if (it.id == id) it.copy(done = !it.done) else it })
    fun remove(c: Context, id: Long) = save(c, all(c).filter { it.id != id })
    fun clearDone(c: Context) = save(c, all(c).filter { !it.done })

    /** النص الي ينقرا بالسيارة */
    fun announcement(c: Context): String? {
        val p = pending(c).map { it.text }
        if (p.isEmpty()) return null
        val list = if (p.size == 1) p[0] else p.dropLast(1).joinToString("، ") + "، و" + p.last()
        return "لا تنسى، عندك: $list."
    }
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("settings", 0)

    fun carEnabled(c: Context) = sp(c).getBoolean("car_enabled", true)
    fun setCarEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("car_enabled", v).apply()

    fun speak(c: Context) = sp(c).getBoolean("speak", true)
    fun setSpeak(c: Context, v: Boolean) = sp(c).edit().putBoolean("speak", v).apply()

    /** اسم بلوتوث السيارة (يكفي جزء منه) */
    fun carName(c: Context) = sp(c).getString("car_name", "Kia") ?: "Kia"
    fun setCarName(c: Context, v: String) = sp(c).edit().putString("car_name", v).apply()

    /** الواجهة القديمة (الكلاسيكية) */
    fun classic(c: Context) = sp(c).getBoolean("classic", false)
    fun setClassic(c: Context, v: Boolean) = sp(c).edit().putBoolean("classic", v).apply()

    fun lastAnnounce(c: Context) = sp(c).getLong("last_announce", 0L)
    fun setLastAnnounce(c: Context, v: Long) = sp(c).edit().putLong("last_announce", v).apply()
}
