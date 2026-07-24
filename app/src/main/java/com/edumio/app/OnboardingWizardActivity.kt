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
import com.google.android.material.textfield.TextInputEditText

class OnboardingWizardActivity : AppCompatActivity() {

    // --- wizard state ---
    private var currentStep = 0
    // v1 onboarding: Welcome → Exam → Name. City / timeline / Italian-level steps were removed from the
    // flow — the step list is the single source of truth in OnboardingSteps.ORDER.
    private val totalSteps = com.edumio.app.core.OnboardingSteps.ORDER.size

    // Ordered set of chosen study areas; the first one becomes the active area.
    private val selectedCareers = LinkedHashSet<CareerPath>()
    // Kept only as defaults for UserGoal; v1 onboarding no longer asks the user for these.
    private val applicationYear = 2026
    private val italianLevel: ItalianLevel = ItalianLevel.A0
    private var studentName = ""

    // --- root views ---
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var btnNext: MaterialButton
    private lateinit var btnBack: ImageButton
    private lateinit var headerDots: LinearLayout
    private lateinit var dotContainer: LinearLayout

    // --- step-specific view caches ---
    private var careerAdapter: CareerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding_wizard)

        viewFlipper = findViewById(R.id.viewFlipper)
        btnNext = findViewById(R.id.btnNext)
        btnBack = findViewById(R.id.btnBack)
        headerDots = findViewById(R.id.headerDots)
        dotContainer = findViewById(R.id.dotContainer)

        // Inflate the v1 steps into the ViewFlipper, strictly from OnboardingSteps.ORDER so the removed
        // city / timeline / level screens are genuinely unreachable (not merely hidden).
        val inflater = LayoutInflater.from(this)
        val stepLayouts = com.edumio.app.core.OnboardingSteps.ORDER.map { step ->
            when (step) {
                com.edumio.app.core.OnboardingSteps.Step.WELCOME -> R.layout.onboarding_step_welcome
                com.edumio.app.core.OnboardingSteps.Step.EXAM -> R.layout.onboarding_step_career
                com.edumio.app.core.OnboardingSteps.Step.NAME -> R.layout.onboarding_step_name
            }
        }
        stepLayouts.forEach { layoutRes ->
            val v = inflater.inflate(layoutRes, viewFlipper, false)
            viewFlipper.addView(v)
        }

        buildDots()
        setupCareerStep()
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

        // v1 is account-free: onboarding runs welcome → exam → … → name → Home with no sign-in step and
        // no network. Everything collected here is persisted locally in finishOnboarding().
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

    private fun setupNameStep() {
        // v1 name step collects ONLY the student's name. The daily target is fixed (5 questions/day) and
        // is not a user choice, so there is no goal picker here.
        val step = viewFlipper.getChildAt(com.edumio.app.core.OnboardingSteps.ORDER.indexOf(com.edumio.app.core.OnboardingSteps.Step.NAME))
        val et = step.findViewById<TextInputEditText>(R.id.etName)

        et.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                studentName = et.text?.toString()?.trim() ?: ""
                true
            } else false
        }
    }

    // ─── validation & data collection ────────────────────────────────────────

    /** Index of the name step in the current flow (Welcome=0, Exam=1, Name=2). */
    private val nameStepIndex
        get() = com.edumio.app.core.OnboardingSteps.ORDER.indexOf(com.edumio.app.core.OnboardingSteps.Step.NAME)

    private fun validateStep(): Boolean {
        return when (currentStep) {
            1 -> { // Exam selection
                if (selectedCareers.isEmpty()) {
                    Toast.makeText(this, "Lütfen en az bir alan seç", Toast.LENGTH_SHORT).show()
                    false
                } else true
            }
            nameStepIndex -> {
                val et = viewFlipper.getChildAt(nameStepIndex).findViewById<TextInputEditText>(R.id.etName)
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
        // Career adapter selection is already live-updated.
        if (currentStep == nameStepIndex) {
            val et = viewFlipper.getChildAt(nameStepIndex).findViewById<TextInputEditText>(R.id.etName)
            val name = et.text?.toString()?.trim() ?: ""
            if (name.isNotBlank()) studentName = name
        }
    }

    // ─── completion ───────────────────────────────────────────────────────────

    private fun finishOnboarding() {
        val name = studentName.ifBlank { "Öğrenci" }
        val careers = selectedCareers.toList().ifEmpty { listOf(CareerPath.OTHER) }
        val primaryCareer = careers.first() // first chosen area becomes the active one
        // v1 onboarding no longer asks for destination cities; default to none.
        val cities = emptyList<String>()

        val goal = UserGoal(
            careerPath = primaryCareer,
            destinationCities = cities,
            applicationYear = applicationYear,
            italianLevel = italianLevel,
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
        // Inactive dots were @color/divider_light (#F3F4F6) on a white header — a 1.06:1 contrast
        // ratio, i.e. invisible. A mid-grey keeps "how many steps are left" readable (UX rule 1).
        val light = ContextCompat.getColor(this, R.color.text_tertiary)
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
        // Inactive dots were @color/divider_light (#F3F4F6) on a white header — a 1.06:1 contrast
        // ratio, i.e. invisible. A mid-grey keeps "how many steps are left" readable (UX rule 1).
        val light = ContextCompat.getColor(this, R.color.text_tertiary)
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
            // v1 selects by EXAM, not degree/career: show the exam code + its verified full name.
            holder.tvEmoji.text = career.emoji
            holder.tvName.text = career.examType.code            // IMAT / TIL-I / CEnT-S
            holder.tvItalian.text = career.examType.fullNameIt   // verified product description

            val isSelected = position in selectedPositions
            val emerald = ContextCompat.getColor(holder.card.context, R.color.emerald)
            val emeraldSoft = ContextCompat.getColor(holder.card.context, R.color.emeraldSoft)
            val outline = ContextCompat.getColor(holder.card.context, R.color.divider_light)
            val density = resources.displayMetrics.density

            // Selection must be obvious at a glance (UX rule 1): a 2dp-vs-1.5dp stroke plus a ~1.09:1
            // fill difference read as "these look the same". A 3dp brand border against a 1dp hairline
            // is an unmistakable step, and the tinted fill reinforces it.
            holder.card.strokeColor = if (isSelected) emerald else outline
            holder.card.setCardBackgroundColor(if (isSelected) emeraldSoft else Color.WHITE)
            holder.card.strokeWidth = if (isSelected) (3 * density).toInt() else (1 * density).toInt()

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
