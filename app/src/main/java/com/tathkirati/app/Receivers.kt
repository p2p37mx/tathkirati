package com.tathkirati.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val FIRE = "fire"
        const val DONE = "done"
        const val SNOOZE = "snooze"
        const val REPLY = "reply"
    }

    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", -1L)
        if (id < 0) return
        when (i.action) {
            FIRE -> {
                val r = Store.get(c, id) ?: return
                Notifier.show(c, r)
                if (r.repeat != 0) Store.put(c, r.copy(time = Scheduler.next(r)))
            }
            DONE -> Actions.done(c, id)
            // الرد الصوتي من السيارة أو زر "أجّل" = تأجيل
            SNOOZE, REPLY -> Actions.snooze(c, id, i.getStringExtra("title"))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.rescheduleAll(c)
    }
}
