package com.brainbuddy.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ExamPackStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.quiz.ExamType

/**
 * Parent-only: Select active exam packs (LGS, TYT, AYT).
 * Quiz generator uses only questions from selected packs.
 */
class ExamPackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return
        setContentView(R.layout.activity_exam_pack)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val store = ExamPackStore(this)
        val chipLGS = findViewById<com.google.android.material.chip.Chip>(R.id.chipLGS)
        val chipTYT = findViewById<com.google.android.material.chip.Chip>(R.id.chipTYT)
        val chipAYT = findViewById<com.google.android.material.chip.Chip>(R.id.chipAYT)
        val chipGeneral = findViewById<com.google.android.material.chip.Chip>(R.id.chipGeneral)

        val active = store.getActiveExamTypes()
        chipLGS.isChecked = ExamType.LGS in active
        chipTYT.isChecked = ExamType.TYT in active
        chipAYT.isChecked = ExamType.AYT in active
        chipGeneral.isChecked = active.isEmpty() || ExamType.GENERAL in active

        fun saveActive() {
            val set = mutableSetOf<ExamType>()
            if (chipGeneral.isChecked) {
                set.add(ExamType.GENERAL)
            } else {
                if (chipLGS.isChecked) set.add(ExamType.LGS)
                if (chipTYT.isChecked) set.add(ExamType.TYT)
                if (chipAYT.isChecked) set.add(ExamType.AYT)
                if (set.isEmpty()) set.add(ExamType.GENERAL)
            }
            store.setActiveExamTypes(set)
        }

        chipLGS.setOnCheckedChangeListener { _, checked ->
            if (checked) chipGeneral.isChecked = false
            saveActive()
        }
        chipTYT.setOnCheckedChangeListener { _, checked ->
            if (checked) chipGeneral.isChecked = false
            saveActive()
        }
        chipAYT.setOnCheckedChangeListener { _, checked ->
            if (checked) chipGeneral.isChecked = false
            saveActive()
        }
        chipGeneral.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                chipLGS.isChecked = false
                chipTYT.isChecked = false
                chipAYT.isChecked = false
            }
            saveActive()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
