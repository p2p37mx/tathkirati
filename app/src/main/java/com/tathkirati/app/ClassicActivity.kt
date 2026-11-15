package com.tathkirati.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

class ClassicActivity : Activity() {
    private val blue = Color.parseColor("#1E5BD8")
    private val bg = Color.parseColor("#F1F4FA")
    private val dark = Color.parseColor("#1B2333")
    private val grey = Color.parseColor("#6B7385")

    private lateinit var listBox: LinearLayout
    private lateinit var titleIn: EditText
    private lateinit var dateBtn: Button
    private lateinit var timeBtn: Button
    private lateinit var repeatGroup: RadioGroup
    private val pick = Calendar.getInstance().apply {
        add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    private val refresh: () -> Unit = { renderList() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(16).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) }
    }

    private fun text(s: String, size: Float, color: Int = dark, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(s: String, filled: Boolean = true, onClick: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 15f
        setTextColor(if (filled) Color.WHITE else blue)
        background = GradientDrawable().apply {
            setColor(if (filled) blue else Color.parseColor("#E6EDFB")); cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        Notifier.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(20), dp(16), dp(24))
            setBackgroundColor(bg)
        }
        root.addView(button("✨ ارجع للواجهة الجديدة (لا تنسى)", false) {
            Prefs.setClassic(this, false)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        root.addView(text("🔔 تذكيراتي", 26f, dark, true))
        root.addView(text("تطلعلك بالتلفون وبشاشة السيارة (أندرويد أوتو)", 14f, grey).apply {
            setPadding(0, dp(2), 0, dp(16))
        })

        // ===== إضافة تذكير =====
        val add = card()
        add.addView(text("تذكير جديد", 18f, dark, true))
        titleIn = EditText(this).apply {
            hint = "شنو تريد أذكرك؟"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 16f
        }
        add.addView(titleIn)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(8)) }
        dateBtn = button("", false) { pickDate() }
        timeBtn = button("", false) { pickTime() }
        row.addView(dateBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        row.addView(timeBtn, LinearLayout.LayoutParams(0, -2, 1f))
        add.addView(row)
        updatePickButtons()

        repeatGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf(0, 1, 2).forEach { r ->
            repeatGroup.addView(RadioButton(this).apply { id = 100 + r; text = Fmt.repeatLabel(r); textSize = 15f })
        }
        repeatGroup.check(100)
        add.addView(repeatGroup)

        add.addView(button("حفظ التذكير") { saveNew() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(add)

        // ===== القائمة =====
        root.addView(text("التذكيرات", 18f, dark, true).apply { setPadding(dp(4), dp(4), 0, dp(8)) })
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        // ===== تجربة =====
        val test = card()
        test.addView(text("جرّب", 18f, dark, true))
        test.addView(text("يسوي تذكير بعد 15 ثانية — شغّل أندرويد أوتو وشوفه يطلع على الشاشة.", 14f, grey))
        test.addView(button("جرّب تذكير بعد 15 ثانية", false) {
            Store.put(this, Reminder(Store.newId(), "هذا تذكير تجريبي ✓", System.currentTimeMillis() + 15_000, 0))
            Toast.makeText(this, "انتظر 15 ثانية…", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        root.addView(test)

        // ===== إعداد أندرويد أوتو =====
        val help = card()
        help.addView(text("🚗 حتى يطلع بأندرويد أوتو", 18f, dark, true))
        help.addView(
            text(
                "1) افتح إعدادات Android Auto وانزل لآخر شي، ودوس على «الإصدار» 10 مرات لحد ما يطلع «وضع المطور».\n" +
                    "2) من النقاط الثلاث فوق ← إعدادات المطوّر ← فعّل «مصادر غير معروفة / Unknown sources».\n" +
                    "3) ارجع لـ «تخصيص مشغّل التطبيقات» وأشّر على «تذكيراتي».\n" +
                    "4) التذكيرات تطلع على شاشة السيارة كرسالة وتنقرا بالصوت، وتكدر تدوس «تم» أو «أجّل».",
                14f, dark
            ).apply { setLineSpacing(dp(4).toFloat(), 1f); setPadding(0, dp(6), 0, 0) }
        )
        if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT < 33) {
            val am = getSystemService(AlarmManager::class.java)
            if (am != null && !am.canScheduleExactAlarms()) {
                help.addView(button("اسمح بالمنبهات الدقيقة", false) {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                    )
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        root.addView(help)

        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(root) })
        Scheduler.rescheduleAll(this)
    }

    override fun onStart() {
        super.onStart()
        Store.listeners += refresh
        renderList()
    }

    override fun onStop() {
        Store.listeners -= refresh
        super.onStop()
    }

    private fun updatePickButtons() {
        dateBtn.text = "📅 " + android.text.format.DateFormat.format("d/M/yyyy", pick)
        timeBtn.text = "🕐 " + Fmt.time(pick.timeInMillis)
    }

    private fun pickDate() {
        DatePickerDialog(this, { _, y, m, d ->
            pick.set(y, m, d); updatePickButtons()
        }, pick.get(Calendar.YEAR), pick.get(Calendar.MONTH), pick.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime() {
        TimePickerDialog(this, { _, h, min ->
            pick.set(Calendar.HOUR_OF_DAY, h); pick.set(Calendar.MINUTE, min); pick.set(Calendar.SECOND, 0)
            updatePickButtons()
        }, pick.get(Calendar.HOUR_OF_DAY), pick.get(Calendar.MINUTE), false).show()
    }

    private fun saveNew() {
        val title = titleIn.text.toString().trim()
        if (title.isEmpty()) {
            titleIn.error = "اكتب شي"; return
        }
        val repeat = repeatGroup.checkedRadioButtonId - 100
        var r = Reminder(Store.newId(), title, pick.timeInMillis, repeat.coerceIn(0, 2))
        if (r.time <= System.currentTimeMillis()) {
            if (r.repeat == 0) {
                Toast.makeText(this, "الوقت فات — اختار وقت جاي", Toast.LENGTH_LONG).show(); return
            }
            r = r.copy(time = Scheduler.next(r))
        }
        Store.put(this, r)
        titleIn.setText("")
        Toast.makeText(this, "انحفظ ✓ ${Fmt.whenText(r)}", Toast.LENGTH_LONG).show()
    }

    private fun renderList() {
        if (!::listBox.isInitialized) return
        listBox.removeAllViews()
        val all = Store.all(this)
        if (all.isEmpty()) {
            listBox.addView(card().apply { addView(text("ماكو تذكيرات بعد", 15f, grey)) })
            return
        }
        all.forEach { r ->
            val c = card()
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(text(r.title, 17f, dark, true))
            info.addView(text(Fmt.whenText(r), 14f, grey))
            line.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(button("حذف", false) { Store.remove(this, r.id); Notifier.cancel(this, r.id) })
            c.addView(line)
            listBox.addView(c)
        }
    }
}
