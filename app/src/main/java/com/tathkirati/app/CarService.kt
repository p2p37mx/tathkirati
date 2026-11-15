package com.tathkirati.app

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** هذا الي يخلي التطبيق يطلع بقائمة تطبيقات أندرويد أوتو */
class CarService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    @Suppress("DEPRECATION")
    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = ListScreen(carContext)
    }
}

/** شاشة تتحدث تلقائيًا لما تتغير التذكيرات */
abstract class LiveScreen(c: CarContext) : Screen(c) {
    private val refresh: () -> Unit = { invalidate() }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Store.listeners += refresh
            }

            override fun onStop(owner: LifecycleOwner) {
                Store.listeners -= refresh
            }
        })
    }

    protected fun listLimit(): Int = try {
        carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    } catch (_: Exception) {
        6
    }
}

class TimedScreen(c: CarContext) : LiveScreen(c) {
    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
            .setNoItemsMessage("ماكو تذكيرات — ضيفها من التلفون أو من «تذكير سريع»")
        Store.all(carContext).take(listLimit()).forEach { r ->
            items.addItem(
                Row.Builder()
                    .setTitle(r.title)
                    .addText(Fmt.whenText(r))
                    .setOnClickListener { screenManager.push(DetailScreen(carContext, r.id)) }
                    .build()
            )
        }
        val strip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("تذكير سريع")
                    .setOnClickListener { screenManager.push(QuickScreen(carContext)) }
                    .build()
            )
            .build()
        return ListTemplate.Builder()
            .setSingleList(items.build())
            .setTitle("تذكيرات بالوقت")
            .setHeaderAction(Action.BACK)
            .setActionStrip(strip)
            .build()
    }
}

class QuickScreen(c: CarContext) : Screen(c) {
    private val options = listOf(
        10 to "بعد 10 دقايق",
        30 to "بعد نص ساعة",
        60 to "بعد ساعة",
        120 to "بعد ساعتين",
    )

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
        options.forEach { (min, label) ->
            items.addItem(
                Row.Builder()
                    .setTitle(label)
                    .addText("تذكير سريع من السيارة")
                    .setOnClickListener {
                        Actions.quick(carContext, min, "تذكير سريع 🚗")
                        CarToast.makeText(carContext, "تم ✓ راح أذكرك $label", CarToast.LENGTH_LONG).show()
                        screenManager.pop()
                    }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setSingleList(items.build())
            .setTitle("ذكرني…")
            .setHeaderAction(Action.BACK)
            .build()
    }
}

class DetailScreen(c: CarContext, private val id: Long) : LiveScreen(c) {
    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val r = Store.get(carContext, id)
            ?: return MessageTemplate.Builder("هذا التذكير انحذف")
                .setTitle("تذكير")
                .setHeaderAction(Action.BACK)
                .build()

        return MessageTemplate.Builder("${r.title}\n\n${Fmt.whenText(r)}")
            .setTitle("تذكير")
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("تم ✓")
                    .setOnClickListener {
                        Actions.done(carContext, id)
                        CarToast.makeText(carContext, "تم ✓", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("أجّل ${Actions.SNOOZE_MIN} د")
                    .setOnClickListener {
                        Actions.snooze(carContext, id, r.title)
                        CarToast.makeText(carContext, "راح أذكرك بعد ${Actions.SNOOZE_MIN} دقايق", CarToast.LENGTH_SHORT).show()
                        screenManager.pop()
                    }
                    .build()
            )
            .build()
    }
}

/** الشاشة الرئيسية بالسيارة: قائمة «لا تنسى» */
class ListScreen(c: CarContext) : LiveScreen(c) {
    init {
        // أول ما تنفتح بالسيارة: اقرا القائمة (إذا ما انقرت قبل شوية)
        if (Prefs.carEnabled(c)) CarWatch.announce(c)
    }

    @Suppress("DEPRECATION")
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder().setNoItemsMessage("ماكو شي ناقص 👌")
        Items.pending(carContext).take(listLimit()).forEach { item ->
            items.addItem(
                Row.Builder()
                    .setTitle(item.text)
                    .addText("دوس عليه لما تشتريه")
                    .setOnClickListener {
                        Items.toggle(carContext, item.id)
                        CarToast.makeText(carContext, "✓ ${item.text}", CarToast.LENGTH_SHORT).show()
                    }
                    .build()
            )
        }
        val strip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("🔊 اقرأ")
                    .setOnClickListener {
                        val t = Items.announcement(carContext)
                        if (t == null) CarToast.makeText(carContext, "القائمة فارغة", CarToast.LENGTH_SHORT).show()
                        else Speaker.speak(carContext, t) {}
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("بالوقت")
                    .setOnClickListener { screenManager.push(TimedScreen(carContext)) }
                    .build()
            )
            .build()
        return ListTemplate.Builder()
            .setSingleList(items.build())
            .setTitle("لا تنسى 🛒")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(strip)
            .build()
    }
}
