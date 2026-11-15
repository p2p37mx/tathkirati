package com.tathkirati.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** الواجهة الجديدة: قائمة «لا تنسى» تنقرا لما تركب السيارة */
class MainActivity : Activity() {
    private val blue = Color.parseColor("#1E5BD8")
    private val indigo = Color.parseColor("#4338CA")
    private val bg = Color.parseColor("#F3F5FB")
    private val dark = Color.parseColor("#141B2D")
    private val grey = Color.parseColor("#7A8296")
    private val green = Color.parseColor("#16A34A")
    private val line = Color.parseColor("#E8EBF3")

    private lateinit var countText: TextView
    private lateinit var subText: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var input: EditText
    private lateinit var carNameText: TextView
    private val refresh: () -> Unit = { render() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(Color.WHITE, 20)
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(14)) }
    }

    private fun tv(s: String, size: Float, color: Int = dark, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun pill(s: String, fg: Int, bgc: Int, onClick: () -> Unit) = TextView(this).apply {
        text = s; textSize = 15f; setTextColor(fg); typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = rounded(bgc, 14)
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.classic(this)) {
            startActivity(Intent(this, ClassicActivity::class.java)); finish(); return
        }
        window.statusBarColor = blue
        Notifier.ensureChannel(this)
        askPermissions()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }

        // ===== الرأس =====
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(blue, indigo)).apply {
                cornerRadius = dp(24).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(16)) }
        }
        header.addView(tv("لا تنسى 🛒", 28f, Color.WHITE, true))
        countText = tv("", 17f, Color.WHITE, true).apply { setPadding(0, dp(10), 0, 0) }
        subText = tv("", 14f, Color.parseColor("#DCE4FF")).apply { setPadding(0, dp(2), 0, 0) }
        header.addView(countText)
        header.addView(subText)
        root.addView(header)

        // ===== الإضافة =====
        val addRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(Color.WHITE, 18)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(6)) }
        }
        input = EditText(this).apply {
            hint = "شنو تحتاج؟ مثلًا: بصل، طماطة، خبز"
            textSize = 16f
            background = null
            setPadding(dp(12), dp(10), dp(12), dp(10))
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_DONE) { addFromInput(); true } else false }
        }
        addRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        addRow.addView(TextView(this).apply {
            text = "+"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(blue) }
            setOnClickListener { addFromInput() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(addRow)
        root.addView(tv("تكدر تكتب أكثر من غرض بنفس الوقت وتفصلهم بفاصلة", 12.5f, grey).apply {
            setPadding(dp(8), 0, dp(8), dp(14))
        })

        // ===== القائمة =====
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        // ===== السيارة =====
        val car = card()
        car.addView(tv("🚗 السيارة", 18f, dark, true).apply { setPadding(0, 0, 0, dp(6)) })
        car.addView(switchRow("ذكرني لما أركب السيارة", "يقرا القائمة لما يتصل التلفون بالسيارة", Prefs.carEnabled(this)) {
            Prefs.setCarEnabled(this, it); render()
        })
        car.addView(divider())
        car.addView(switchRow("اقرأها بالصوت", "تطلع من سماعات السيارة", Prefs.speak(this)) { Prefs.setSpeak(this, it) })
        car.addView(divider())
        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { editCarName() }
        }
        val nameInfo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nameInfo.addView(tv("اسم بلوتوث السيارة", 16f, dark))
        carNameText = tv("", 13f, grey)
        nameInfo.addView(carNameText)
        nameRow.addView(nameInfo, LinearLayout.LayoutParams(0, -2, 1f))
        nameRow.addView(tv("تغيير", 14f, blue, true))
        car.addView(nameRow)
        car.addView(pill("🔊 جرّب هسه — اقرالي القائمة", blue, Color.parseColor("#E6EDFB")) {
            if (!CarWatch.announce(this, force = true)) Toast.makeText(this, "القائمة فارغة — ضيف أغراض أول", Toast.LENGTH_SHORT).show()
        }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) } })
        root.addView(car)

        // ===== رجوع للواجهة القديمة =====
        root.addView(tv("↩︎ الواجهة القديمة (تذكيرات بالوقت)", 14f, grey).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
            setOnClickListener {
                Prefs.setClassic(this@MainActivity, true)
                startActivity(Intent(this@MainActivity, ClassicActivity::class.java))
                finish()
            }
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
        })
    }

    override fun onStart() {
        super.onStart()
        if (!::listBox.isInitialized) return
        Store.listeners += refresh
        render()
    }

    override fun onStop() {
        Store.listeners -= refresh
        super.onStop()
    }

    private fun askPermissions() {
        val need = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) need += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) need += Manifest.permission.BLUETOOTH_CONNECT
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(-1, dp(1))
    }

    private fun switchRow(title: String, sub: String, on: Boolean, change: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(tv(title, 16f, dark))
        info.addView(tv(sub, 13f, grey))
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply {
            isChecked = on
            setOnCheckedChangeListener { _, v -> change(v) }
        })
        return row
    }

    private fun addFromInput() {
        val n = Items.add(this, input.text.toString())
        if (n > 0) input.setText("")
    }

    private fun editCarName() {
        val e = EditText(this).apply { setText(Prefs.carName(this@MainActivity)); setSelection(text.length) }
        val box = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(e) }
        AlertDialog.Builder(this)
            .setTitle("اسم بلوتوث السيارة")
            .setMessage("يكفي جزء من الاسم، مثل Kia")
            .setView(box)
            .setPositiveButton("حفظ") { _, _ ->
                Prefs.setCarName(this, e.text.toString().trim().ifEmpty { "Kia" }); render()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun render() {
        val all = Items.all(this)
        val pending = all.filter { !it.done }
        val done = all.filter { it.done }

        countText.text = if (pending.isEmpty()) "ماكو شي ناقص 👌" else "عندك ${pending.size} ${if (pending.size == 1) "غرض" else "أغراض"} لا تنساها"
        subText.text = if (Prefs.carEnabled(this)) "راح أقرالك ياها لما تركب الـ ${Prefs.carName(this)} 🚗" else "التذكير بالسيارة مطفي"
        carNameText.text = Prefs.carName(this)

        listBox.removeAllViews()
        if (all.isEmpty()) {
            listBox.addView(card().apply {
                gravity = Gravity.CENTER
                addView(tv("📝", 34f).apply { gravity = Gravity.CENTER })
                addView(tv("القائمة فارغة", 16f, dark, true).apply { gravity = Gravity.CENTER })
                addView(tv("اكتب الأغراض الي تحتاجها فوق", 14f, grey).apply { gravity = Gravity.CENTER })
            })
            return
        }

        val c = card().apply { setPadding(dp(6), dp(4), dp(6), dp(4)) }
        (pending + done).forEachIndexed { idx, item ->
            if (idx > 0) c.addView(divider())
            c.addView(itemRow(item))
        }
        listBox.addView(c)

        if (done.isNotEmpty()) {
            listBox.addView(pill("🧹 امسح الي اشتريتها (${done.size})", grey, Color.parseColor("#E9ECF3")) {
                Items.clearDone(this)
            }.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) } })
        }
    }

    private fun itemRow(item: Item): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(12), dp(10), dp(12))
            setOnClickListener { Items.toggle(this@MainActivity, item.id) }
        }
        val check = TextView(this).apply {
            text = if (item.done) "✓" else ""
            textSize = 15f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (item.done) setColor(green) else { setColor(Color.WHITE); setStroke(dp(2), Color.parseColor("#C5CBD8")) }
            }
        }
        row.addView(check, LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(14) })
        val t = tv(item.text, 17f, if (item.done) grey else dark).apply {
            if (item.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(tv("✕", 16f, Color.parseColor("#B4BAC8")).apply {
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener { Items.remove(this@MainActivity, item.id) }
        })
        return row
    }
}
