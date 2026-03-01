package com.brainbuddy.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ScheduleStore

class SchedulesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        setContentView(R.layout.activity_schedules)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val scheduleStore = ScheduleStore(this)
        val rules = scheduleStore.getRules()
        findViewById<android.widget.TextView>(R.id.tvSchedulesList).text =
            if (rules.isEmpty()) getString(R.string.schedule_empty)
            else rules.joinToString("\n") { r ->
                val days = r.daysOfWeek.joinToString(",") { when(it) { 0->"Paz" 1->"Pzt" 2->"Sal" 3->"Çar" 4->"Per" 5->"Cum" 6->"Cmt" else->"?" } }
                "${days} ${r.startMinuteOfDay/60}:${"%02d".format(r.startMinuteOfDay%60)}-${r.endMinuteOfDay/60}:${"%02d".format(r.endMinuteOfDay%60)} → ${r.targetGroup}"
            }

        findViewById<android.widget.Button>(R.id.btnAddSchedule).setOnClickListener {
            val rule = ScheduleStore.ScheduleRule(
                id = "rule_${System.currentTimeMillis()}",
                daysOfWeek = setOf(1,2,3,4,5),
                startMinuteOfDay = 19 * 60,
                endMinuteOfDay = 21 * 60,
                targetGroup = "social"
            )
            scheduleStore.addRule(rule)
            Toast.makeText(this, getString(R.string.schedule_added), Toast.LENGTH_SHORT).show()
            recreate()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
