package com.edumio.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.edumio.app.R
import com.edumio.app.core.GamificationStore
import com.edumio.app.core.StudentProfileStore
import com.edumio.app.core.UserGoalPrefs
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * MVP profile: the student's personal journey — display name, active study area
 * (exam + career), and real XP / level / streak — plus quick links to their study
 * areas and app settings. No store, no locked mascots, no premium, no empty-badge
 * surfaces: a single default EDUmio avatar represents the account.
 */
class StudentProfileActivity : AppCompatActivity() {

    private lateinit var store: StudentProfileStore
    private lateinit var gam: GamificationStore
    private lateinit var goalPrefs: UserGoalPrefs
    private lateinit var tvDisplayName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        store = StudentProfileStore(this)
        gam = GamificationStore(this)
        goalPrefs = UserGoalPrefs(this)

        tvDisplayName = findViewById(R.id.tvDisplayName)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        val content = findViewById<View>(R.id.scrollContent)
        val origBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, origBottom + navBottom)
            insets
        }

        refreshAll()
        setupNavigation()
    }

    override fun onResume() {
        super.onResume()
        refreshIdentityHero()
    }

    private fun refreshAll() {
        refreshIdentityHero()
        refreshNameEdit()
        refreshGoalCard()
    }

    private fun refreshIdentityHero() {
        val displayName = store.getDisplayName().ifBlank { goalPrefs.getStudentName() }
        tvDisplayName.text = displayName.ifBlank { "—" }

        findViewById<TextView>(R.id.tvStreak).text = "${gam.streakDays()}"
        findViewById<TextView>(R.id.tvXp).text = "${gam.xp()}"
        findViewById<TextView>(R.id.tvLevel).text = "${gam.level()}"
    }

    private fun refreshNameEdit() {
        val etName = findViewById<android.widget.EditText>(R.id.etDisplayName)
        etName.setText(store.getDisplayName())
        etName.hint = getString(R.string.student_profile_name_hint)

        findViewById<View>(R.id.btnSaveName).setOnClickListener {
            val name = etName.text.toString().trim()
            store.setDisplayName(name)
            tvDisplayName.text = name.ifBlank { "—" }
            android.widget.Toast.makeText(this, getString(R.string.profile_name_saved), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshGoalCard() {
        val exam = goalPrefs.getGoal().careerPath.examType

        // v1 is exam-framed: show ONLY the active exam (IMAT / TIL-I / CEnT-S) — never the degree name
        // (e.g. "Ingegneria") or the career journey, and no city/level.
        findViewById<TextView>(R.id.tvCareerGoal).text = "🇮🇹 ${exam.code}"
        findViewById<TextView>(R.id.tvGoalDegree).visibility = View.GONE
        findViewById<TextView>(R.id.tvGoalCities).visibility = View.GONE
        findViewById<ChipGroup>(R.id.chipGroupGoalMeta).visibility = View.GONE
    }

    private fun makeReadOnlyChip(text: String): Chip {
        return Chip(this, null, R.style.Widget_EDUmio_Chip_Stat).apply {
            this.text = text
            isClickable = false
            isCheckable = false
        }
    }

    private fun setupNavigation() {
        findViewById<MaterialCardView>(R.id.cardStudyAreas).setOnClickListener {
            startActivity(Intent(this, StudyAreasActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
