package dev.nossam.task

import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.time.ZonedDateTime

class AlarmActivity : AppCompatActivity() {
    private val clock = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val now = ZonedDateTime.now(dev.nossam.task.plan.MemoPlanner.ZONE)
            findViewById<TextView>(R.id.alarmClock)?.text = "%02d:%02d".format(now.hour, now.minute)
            clock.postDelayed(this, 1000L)
        }
    }
    private var titleText = "アラーム"
    private var detailText = ""
    private var kindText = ""
    private var itemText = ""
    private var tabText = ""
    private var dateText = ""
    private var timeText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prepareEdgeToEdge()
        setContentView(R.layout.activity_alarm)
        applySafeSystemBarPadding()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        volumeControlStream = AudioManager.STREAM_ALARM
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )
        findViewById<Button>(R.id.alarmStop).setOnClickListener { dismiss() }
        findViewById<Button>(R.id.alarmSnooze).setOnClickListener { snooze() }
        show(intent)
        val volume = getSystemService(AudioManager::class.java)?.getStreamVolume(AudioManager.STREAM_ALARM) ?: 1
        findViewById<TextView>(R.id.alarmVolume).visibility =
            if (volume == 0) android.view.View.VISIBLE else android.view.View.GONE
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    override fun onStart() {
        super.onStart()
        clock.post(tick)
    }

    override fun onStop() {
        clock.removeCallbacks(tick)
        super.onStop()
    }

    private fun show(intent: Intent) {
        titleText = intent.getStringExtra(AlarmIntents.EXTRA_TITLE)?.ifBlank { null } ?: "アラーム"
        detailText = intent.getStringExtra(AlarmIntents.EXTRA_DETAIL).orEmpty()
        kindText = intent.getStringExtra(AlarmIntents.EXTRA_KIND).orEmpty()
        itemText = intent.getStringExtra(AlarmIntents.EXTRA_ITEM).orEmpty()
        tabText = intent.getStringExtra(AlarmIntents.EXTRA_TAB).orEmpty()
        dateText = intent.getStringExtra(AlarmIntents.EXTRA_DATE).orEmpty()
        timeText = intent.getStringExtra(AlarmIntents.EXTRA_TIME).orEmpty()
        findViewById<TextView>(R.id.alarmTitle).text = titleText
        findViewById<TextView>(R.id.alarmDetail).text = detailText
    }

    private fun dismiss() {
        startService(itemIntent(AlarmIntents.ACTION_DISMISS))
        finish()
    }

    private fun snooze() {
        startService(itemIntent(AlarmIntents.ACTION_SNOOZE))
        finish()
    }

    private fun itemIntent(action: String): Intent {
        return Intent(this, AlarmRingService::class.java).setAction(action).apply {
            putExtra(AlarmIntents.EXTRA_TITLE, titleText)
            putExtra(AlarmIntents.EXTRA_DETAIL, detailText)
            putExtra(AlarmIntents.EXTRA_KIND, kindText)
            putExtra(AlarmIntents.EXTRA_ITEM, itemText)
            putExtra(AlarmIntents.EXTRA_TAB, tabText)
            putExtra(AlarmIntents.EXTRA_DATE, dateText)
            putExtra(AlarmIntents.EXTRA_TIME, timeText)
        }
    }
}
