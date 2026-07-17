package com.edumio.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.edumio.app.core.CareerPath
import com.edumio.app.core.ItalianLevel
import com.edumio.app.core.OnboardingPrefs
import com.edumio.app.core.ProfileStore
import com.edumio.app.core.StudyAreaManager
import com.edumio.app.core.UserGoal
import com.edumio.app.core.UserGoalPrefs
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText

class OnboardingWizardActivity : AppCompatActivity() {

    // --- wizard state ---
    private var currentStep = 0
    private val totalSteps = 6  // steps 0..5

    // Ordered set of chosen study areas; the first one becomes the active area.
    private val selectedCareers = LinkedHashSet<CareerPath>()
    private val selectedCities = mutableSetOf<String>()
    private var applicationYear = 2026
    private var italianLevel: ItalianLevel = ItalianLevel.A0
    private var studentName = ""
    private var dailyGoalQuestions = 15

    // --- root views ---
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: ImageButton
    private lateinit var headerDots: LinearLayout
    private lateinit var dotContainer: LinearLayout

    // --- step-specific view caches ---
    private var careerAdapter: CareerAdapter? = null
    private val timelineCards = mutableListOf<MaterialCardView>()
    private val levelCards = mutableListOf<MaterialCardView>()
    private var timelineYears = listOf(2026, 2027, 2028, 0) // 0 = exploring

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_wizard)

        viewFlipper = findViewById(R.id.viewFlipper)
        btnNext = findViewById(R.id.btnNext)
        btnBack = findViewById(R.id.btnBack)
        headerDots = findViewById(R.id.headerDots)
        dotContainer = findViewById(R.id.dotContainer)

        // Inflate all 6 steps into the ViewFlipper
        val inflater = LayoutInflater.from(this)
        val stepLayouts = listOf(
            R.layout.onboarding_step_welcome,
            R.layout.onboarding_step_career,
            R.layout.onboarding_step_city,
            R.layout.onboarding_step_timeline,
            R.layout.onboarding_step_level,
            R.layout.onboarding_step_name
        )
        stepLayouts.forEach { layoutRes ->
            val v = inflater.inflate(layoutRes, viewFlipper, false)
            viewFlipper.addView(v)
        }

        buildDots()
        setupCareerStep()
        setupCityStep()
        setupTimelineStep()
        setupLevelStep()
        setupNameStep()

        renderStep(animate = false)

        btnNext.setOnClickListener { onNextClicked() }
        btnBack.setOnClickListener { onBackClicked() }

        // Apply safe bottom padding to the footer so the CTA is never hidden
        // behind the gesture navigation bar on real devices.
        val footer = findViewById<LinearLayout>(R.id.wizardFooter)
        val origFooterBottom = footer.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(footer) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, origFooterBottom + navBottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { onBackClicked() }
        })
    }

    // ─── step navigation ─────────────────────────────────────────────────────

    private fun onNextClicked() {
        if (!validateStep()) return
        collectStepData()

        if (currentStep == totalSteps - 1) {
            finishOnboarding()
            return
        }

        val forward = true
        currentStep++
        animateTo(forward)
        renderStep(animate = true)
    }

    private fun onBackClicked() {
        if (currentStep == 0) { finish(); return }
        collectStepData()
        currentStep--
        animateTo(forward = false)
        renderStep(animate = true)
    }

    private fun animateTo(forward: Boolean) {
        viewFlipper.inAnimation = AnimationUtils.loadAnimation(
            this,
            if (forward) R.anim.slide_in_right else R.anim.slide_in_left
        )
        viewFlipper.outAnimation = AnimationUtils.loadAnimation(
            this,
            if (forward) R.anim.slide_out_left else R.anim.slide_out_right
        )
        viewFlipper.displayedChild = currentStep
    }

    private fun renderStep(animate: Boolean) {
        if (!animate) viewFlipper.displayedChild = currentStep

        // Header / back button
        val isWelcome = currentStep == 0
        headerDots.visibility = if (isWelcome) View.GONE else View.VISIBLE
        btnBack.visibility = if (isWelcome) View.INVISIBLE else View.VISIBLE

        // Next button label
        btnNext.text = when (currentStep) {
            0 -> "Hadi Başlayalım ✨"
            totalSteps - 1 -> "Başla! 🚀"
            else -> "İleri →"
        }

        // Next button enabled state
        updateNextEnabled()

        // Active dot
        if (!isWelcome) highlightDot(currentStep - 1) // dots are 0-indexed for steps 1-5
    }

    private fun updateNextEnabled() {
        btnNext.isEnabled = when (currentStep) {
            1 -> selectedCareers.isNotEmpty()
            else -> true
        }
        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.5f
    }

    // ─── step setup ──────────────────────────────────────────────────────────

    private fun setupCareerStep() {
        val step = viewFlipper.getChildAt(1)
        val rv = step.findViewById<RecyclerView>(R.id.rvCareers)
        // Multi-select: tapping toggles a career in/out of the chosen set.
        careerAdapter = CareerAdapter(com.edumio.app.core.ExamFocus.selectableCareers()) { career, selected ->
            if (selected) selectedCareers.add(career) else selectedCareers.remove(career)
            updateNextEnabled()
        }
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = careerAdapter
        rv.isNestedScrollingEnabled = true
    }

    private fun setupCityStep() {
        val step = viewFlipper.getChildAt(2)
        val chipGroup = step.findViewById<ChipGroup>(R.id.chipGroupCities)

        // Pairs of (display text, canonical city key) — keys avoid emoji parsing issues
        val cities = listOf(
            "🏛️  Roma" to "Roma",
            "🏙️  Milano" to "Milano",
            "🎓  Bologna" to "Bologna",
            "🌸  Firenze" to "Firenze",
            "⚽  Torino" to "Torino",
            "☀️  Napoli" to "Napoli",
            "📖  Padova" to "Padova",
            "🌊  Palermo" to "Palermo",
            "🗺️  Henüz bilmiyorum" to "Henüz bilmiyorum"
        )

        cities.forEach { (displayText, key) ->
            val chip = Chip(this).apply {
                text = displayText
                tag = key
                isCheckable = true
                chipCornerRadius = 24f
                setChipBackgroundColorResource(R.color.white)
                setChipStrokeColorResource(R.color.divider_light)
                chipStrokeWidth = 3f
            }
            chip.setOnCheckedChangeListener { _, checked ->
                val cityKey = chip.tag as String
                if (cityKey == "Henüz bilmiyorum") {
                    if (checked) {
                        selectedCities.clear()
                        selectedCities.add("Henüz bilmiyorum")
                        chipGroup.children.filterIsInstance<Chip>().forEach { c ->
                            if (c != chip) c.isChecked = false
                        }
                    } else {
                        selectedCities.remove("Henüz bilmiyorum")
                    }
                } else {
                    if (checked) {
                        selectedCities.add(cityKey)
                        chipGroup.children.filterIsInstance<Chip>()
                            .find { (it.tag as? String) == "Henüz bilmiyorum" }
                            ?.isChecked = false
                        selectedCities.remove("Henüz bilmiyorum")
                    } else {
                        selectedCities.remove(cityKey)
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun setupTimelineStep() {
        val step = viewFlipper.getChildAt(3)
        val cardIds = listOf(R.id.cardTimeline1, R.id.cardTimeline2, R.id.cardTimeline3, R.id.cardTimeline4)
        timelineYears = listOf(2026, 2027, 2028, 0)

        cardIds.forEachIndexed { i, id ->
            val card = step.findViewById<MaterialCardView>(id)
            timelineCards.add(card)
            card.setOnClickListener {
                applicationYear = timelineYears[i]
                selectOptionCard(card, timelineCards)
            }
        }
        // Default selection = option 2 (next year)
        applicationYear = 2027
        selectOptionCard(timelineCards[1], timelineCards)
    }

    private fun setupLevelStep() {
        val step = viewFlipper.getChildAt(4)
        val cardIds = listOf(R.id.cardLevel1, R.id.cardLevel2, R.id.cardLevel3, R.id.cardLevel4)
        val levels = ItalianLevel.values()

        cardIds.forEachIndexed { i, id ->
            val card = step.findViewById<MaterialCardView>(id)
            levelCards.add(card)
            card.setOnClickListener {
                italianLevel = levels[i]
                selectOptionCard(card, levelCards)
            }
        }
        // Default = A0
        italianLevel = ItalianLevel.A0
        selectOptionCard(levelCards[0], levelCards)
    }

    private fun setupNameStep() {
        val step = viewFlipper.getChildAt(5)
        val et = step.findViewById<TextInputEditText>(R.id.etName)
        val chipGroup = step.findViewById<ChipGroup>(R.id.chipGroupGoal)

        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                studentName = et.text?.toString()?.trim() ?: ""
                true
            } else false
        }

        chipGroup.setOnCheckedStateChangeListener { group, _ ->
            val checkedId = group.checkedChipId
            dailyGoalQuestions = when (checkedId) {
                R.id.chipGoal5 -> 5
                R.id.chipGoal30 -> 30
                else -> 15
            }
        }
    }

    // ─── validation & data collection ────────────────────────────────────────

    private fun validateStep(): Boolean {
        return when (currentStep) {
            1 -> {
                if (selectedCareers.isEmpty()) {
                    Toast.makeText(this, "Lütfen en az bir alan seç", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            5 -> {
                val et = viewFlipper.getChildAt(5).findViewById<TextInputEditText>(R.id.etName)
                studentName = et.text?.toString()?.trim() ?: ""
                if (studentName.isBlank()) {
                    studentName = "Öğrenci"
                }
                true
            }
            else -> true
        }
    }

    private fun collectStepData() {
        // Career adapter selection is already live-updated
        // Cities are live-updated via chip listeners
        // Timeline and level are live-updated via card listeners
        if (currentStep == 5) {
            val et = viewFlipper.getChildAt(5).findViewById<TextInputEditText>(R.id.etName)
            val name = et.text?.toString()?.trim() ?: ""
            if (name.isNotBlank()) studentName = name
        }
    }

    // ─── completion ───────────────────────────────────────────────────────────

    private fun finishOnboarding() {
        val name = studentName.ifBlank { "Öğrenci" }
        val careers = selectedCareers.toList().ifEmpty { listOf(CareerPath.OTHER) }
        val primaryCareer = careers.first() // first chosen area becomes the active one
        val cities = selectedCities.toList().ifEmpty { emptyList() }

        val goal = UserGoal(
            careerPath = primaryCareer,
            destinationCities = cities,
            applicationYear = applicationYear,
            italianLevel = italianLevel,
            dailyGoalQuestions = dailyGoalQuestions,
            studentName = name
        )

        // Save goal (career mirrors the active study area)
        UserGoalPrefs(this).saveGoal(goal)

        // Set up study-area profiles: the "default" profile is the primary area;
        // each additional chosen career becomes its own area with isolated stats.
        val profileStore = ProfileStore(this)
        val profiles = profileStore.getProfiles().toMutableList()
        val defaultIdx = profiles.indexOfFirst { it.id == "default" }
        if (defaultIdx >= 0) {
            profiles[defaultIdx] = profiles[defaultIdx].copy(name = name, careerPath = primaryCareer.name)
            profileStore.setProfiles(profiles)
        } else {
            profileStore.addProfile(ProfileStore.Profile(id = "default", name = name, careerPath = primaryCareer.name))
        }
        profileStore.setCurrentProfileId("default")
        careers.drop(1).forEach { StudyAreaManager.addArea(this, it) }

        OnboardingPrefs.setDone(this, true)
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    // ─── dot indicators ──────────────────────────────────────────────────────

    private fun buildDots() {
        dotContainer.removeAllViews()
        val activeDots = totalSteps - 1 // steps 1..5
        val emerald = ContextCompat.getColor(this, R.color.emerald)
        val light = ContextCompat.getColor(this, R.color.divider_light)
        val dp6 = (6 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        repeat(activeDots) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp6, dp6).also { lp ->
                    lp.marginStart = dp8 / 2
                    lp.marginEnd = dp8 / 2
                }
                setBackgroundResource(R.drawable.shape_dot)
                background?.setTint(light)
            }
            dotContainer.addView(dot)
        }
    }

    private fun highlightDot(activeDotIndex: Int) {
        val emerald = ContextCompat.getColor(this, R.color.emerald)
        val light = ContextCompat.getColor(this, R.color.divider_light)
        dotContainer.children.forEachIndexed { i, v ->
            v.background?.setTint(if (i == activeDotIndex) emerald else light)
            val size = if (i == activeDotIndex) {
                (8 * resources.displayMetrics.density).toInt()
            } else {
                (6 * resources.displayMetrics.density).toInt()
            }
            v.layoutParams = (v.layoutParams as LinearLayout.LayoutParams).apply {
                width = size; height = size
            }
        }
    }

    // ─── option card selection helper ─────────────────────────────────────────

    private fun selectOptionCard(selected: MaterialCardView, all: List<MaterialCardView>) {
        val emerald = ContextCompat.getColor(this, R.color.emerald)
        val emeraldSoft = ContextCompat.getColor(this, R.color.emeraldSoft)
        val outline = ContextCompat.getColor(this, R.color.divider_light)
        all.forEach { card ->
            if (card == selected) {
                card.strokeColor = emerald
                card.setCardBackgroundColor(emeraldSoft)
                card.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            } else {
                card.strokeColor = outline
                card.setCardBackgroundColor(Color.WHITE)
                card.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
            }
        }
    }

    // ─── career RecyclerView adapter ─────────────────────────────────────────

    private inner class CareerAdapter(
        private val items: List<CareerPath>,
        private val onToggled: (CareerPath, Boolean) -> Unit
    ) : RecyclerView.Adapter<CareerAdapter.VH>() {

        // Multi-select: positions the user has toggled on.
        private val selectedPositions = mutableSetOf<Int>()

        inner class VH(val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
            val tvEmoji: TextView = card.findViewById(R.id.tvEmoji)
            val tvName: TextView = card.findViewById(R.id.tvCareerName)
            val tvItalian: TextView = card.findViewById(R.id.tvItalianName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_career_card, parent, false) as MaterialCardView
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val career = items[position]
            holder.tvEmoji.text = career.emoji
            holder.tvName.text = career.displayNameTr
            holder.tvItalian.text = career.italianDegreeName

            val isSelected = position in selectedPositions
            val emerald = ContextCompat.getColor(holder.card.context, R.color.emerald)
            val emeraldSoft = ContextCompat.getColor(holder.card.context, R.color.emeraldSoft)
            val outline = ContextCompat.getColor(holder.card.context, R.color.divider_light)
            val density = resources.displayMetrics.density

            holder.card.strokeColor = if (isSelected) emerald else outline
            holder.card.setCardBackgroundColor(if (isSelected) emeraldSoft else Color.WHITE)
            holder.card.strokeWidth = if (isSelected) (2 * density).toInt() else (1.5f * density).toInt()

            holder.card.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val nowSelected = if (pos in selectedPositions) {
                    selectedPositions.remove(pos); false
                } else {
                    selectedPositions.add(pos); true
                }
                notifyItemChanged(pos)
                onToggled(items[pos], nowSelected)
            }
        }

        override fun getItemCount() = items.size
    }
}
