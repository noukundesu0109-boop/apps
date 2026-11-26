package dev.nossam.task

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.nossam.task.plan.MemoPlanner
import java.time.LocalDate
import java.time.ZonedDateTime

class MoveDateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_move_date)
        show(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent) {
        val kind = intent.getStringExtra(AlarmIntents.EXTRA_KIND).orEmpty()
        val itemId = intent.getStringExtra(AlarmIntents.EXTRA_ITEM).orEmpty()
        val tabId = intent.getStringExtra(AlarmIntents.EXTRA_TAB).orEmpty()
        val title = intent.getStringExtra(AlarmIntents.EXTRA_TITLE).orEmpty().ifBlank { "予定" }
        findViewById<TextView>(R.id.move_title).text = title
        val dates = findViewById<LinearLayout>(R.id.move_dates)
        dates.removeAllViews()
        val today = ZonedDateTime.now(MemoPlanner.ZONE).toLocalDate()
        for (offset in 0L..6L) {
            val date = today.plusDays(offset)
            val button = Button(this)
            button.text = MemoPlanner.widgetDateLabel(date, today)
            button.setOnClickListener { choose(kind, itemId, tabId, date) }
            dates.addView(button)
        }
        findViewById<Button>(R.id.move_other).setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, day -> choose(kind, itemId, tabId, LocalDate.of(year, month + 1, day)) },
                today.year,
                today.monthValue - 1,
                today.dayOfMonth,
            ).show()
        }
        findViewById<Button>(R.id.move_cancel).setOnClickListener { finish() }
    }

    private fun choose(kind: String, itemId: String, tabId: String, date: LocalDate) {
        if (kind.isBlank() || itemId.isBlank()) {
            finish()
            return
        }
        TaskAlarms.move(this, kind, itemId, tabId, date.toString())
        finish()
    }
}
