package com.edumio.app.quiz

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import com.edumio.app.BuildConfig
import com.edumio.app.R
import com.edumio.app.core.LastTestUnlockStore
import com.edumio.app.core.PremiumStore
import com.edumio.app.HomeActivity
import com.edumio.app.core.RetryUnlockStore
import com.edumio.app.core.QuizRetryPolicy
import com.edumio.app.core.QuizPrefs
import com.edumio.app.databinding.ActivityQuizBinding
import com.edumio.app.quiz.LevelGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.edumio.app.MainActivity
import com.edumio.app.db.StartupRuntimeState
import kotlinx.coroutines.flow.first
import java.util.UUID

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RETRY_WRONG = "retry_wrong"
        const val EXTRA_WRONG_IDS = "wrong_ids"
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_IS_RETRY = "is_retry"
        const val EXTRA_QUESTIONS_JSON = "questions_json"
        const val EXTRA_REMEDIAL = "remedial"
        const val EXTRA_REPLAY_FROM_LAST_TEST = "replay_from_last_test"
        const val EXTRA_UNLOCK_TOKEN = "unlock_token"
        const val EXTRA_QUESTION_IDS_FOR_REPLAY = "question_ids_for_replay"
        const val EXTRA_RETRY_AFTER_AD = "retry_after_ad"
        const val EXTRA_RETRY_UNLOCK_TOKEN = "retry_unlock_token"
        const val EXTRA_SUBJECT_FILTER = "subject_filter"
        /** Daily-mission category set (DB Subject enum names, comma-separated) to draw from. */
        const val EXTRA_MISSION_CATEGORIES = "mission_categories"
        /** Soft-fail minimum: serve shorter quiz down to this count instead of hard-failing. */
        private const val ABSOLUTE_MIN_QUESTIONS = 5

        /**
         * Runtime option randomization. The official IMAT bank in the DB matches the source PDFs
         * verbatim (Form-A papers keep the correct answer at option A). To keep the quiz from being
         * positionally gameable, the DISPLAY order of A–E options is randomized here at runtime —
         * never persisted to the database. Set false to show options in official PDF order.
         */
        const val SHUFFLE_OPTIONS_AT_RUNTIME = true
    }

    private lateinit var b: ActivityQuizBinding
    private lateinit var repo: QuestionRepository
    private lateinit var quizPrefs: QuizPrefs

    private var questions: List<Question> = emptyList()
    private var index = 0
    private var retryWrongMode = false
    private var isRetryOfLockedQuiz = false
    private var quizId: String = ""
    private var startedAt: Long = 0L

    /** answers[questionId] = the ORIGINAL (official, PDF) option index the user selected. */
    private val answers = mutableMapOf<String, Int>()
    /** Stable per-question display permutation: optionOrders[id][displayPos] = originalIndex. */
    private val optionOrders = mutableMapOf<String, IntArray>()
    private var isFinishing = false

    // Debug-only diagnostics (grade mode)
    private var poolDebug: QuestionRepository.PoolDebugForGrade? = null
    private var debugWrongUsed: Int = 0
    private var pickerDebugPath: String = ""
    private var isLgsModeForDebug: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityQuizBinding.inflate(layoutInflater)
        setContentView(b.root)

        try {
            initQuiz(savedInstanceState)
        } catch (e: OutOfMemoryError) {
            android.widget.Toast.makeText(this, getString(R.string.quiz_oom_error), android.widget.Toast.LENGTH_LONG).show()
            finish()
        } catch (_: Exception) {
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFinishing) return
                MaterialAlertDialogBuilder(this@QuizActivity)
                    .setTitle(getString(R.string.quiz_abandon_title))
                    .setMessage(getString(R.string.quiz_abandon_message))
                    .setPositiveButton(getString(R.string.quiz_abandon_confirm)) { _, _ -> finish() }
                    .setNegativeButton(getString(R.string.quiz_abandon_cancel), null)
                    .show()
            }
        })
    }


    /** Controlled error when an exam's question bank is empty — no fake question, no wrong-domain fallback. */
    private fun showEmptyPoolError() {
        b.loadingBar.visibility = View.GONE
        b.questionCard.visibility = View.GONE
        b.optionsGroup.visibility = View.GONE
        b.submitBtn.visibility = View.GONE
        b.nextBtn.visibility = View.GONE
        b.subjectChip.visibility = View.GONE
        b.progressText.visibility = View.GONE
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Soru havuzu hazır değil")
            .setMessage("Bu sınav için soru havuzu şu anda hazır değil. Lütfen daha sonra tekrar dene.")
            .setCancelable(false)
            .setPositiveButton("Tamam") { _, _ -> finish() }
            .show()
    }

    private fun initQuiz(savedInstanceState: Bundle?) {
        repo = QuestionRepository(this)
        quizPrefs = QuizPrefs(this)

        // Real loading indicator (NOT a fake "Test hazırlanıyor" question): hide every question widget and
        // show a spinner. The counter/question card/Next are never populated with placeholder text, so the
        // loading state is not counted as question 1 and Next cannot be pressed.
        b.loadingBar.visibility = View.VISIBLE
        b.questionCard.visibility = View.GONE
        b.subjectChip.visibility = View.GONE
        b.progressText.visibility = View.GONE
        b.optionsGroup.visibility = View.GONE
        b.submitBtn.visibility = View.GONE
        b.nextBtn.visibility = View.GONE
        b.nextBtn.isEnabled = false
        b.nextBtn.setOnClickListener { }

        lifecycleScope.launch {
            // Wait for DB seed+integrity to complete before building quiz.
            // Without this gate, the quiz can run against an empty/partial DB
            // on first launch and show "Soru havuzu yetersiz".
            if (!StartupRuntimeState.startupInitializationComplete) {
                // (loadingBar already visible) — Observe the startup flow — proceed as soon as Ready fires.
                // Timeout is generous (15s) to handle slow first-install seeding
                // of 20k+ questions. If it expires we proceed anyway and let the
                // quiz builder handle whatever state the DB is in.
                val ready = withTimeoutOrNull(15_000L) {
                    StartupRuntimeState.phase.first { it is StartupRuntimeState.StartupPhase.Ready }
                }
                if (ready == null) {
                    android.util.Log.w("QuizActivity", "Startup gate timed out after 15s — proceeding with current DB state")
                }
            }
            val targetCount = quizPrefs.questionsPerSession()
            val isReplayFromLastTest = intent.getBooleanExtra(EXTRA_REPLAY_FROM_LAST_TEST, false)
            if (isReplayFromLastTest) {
                val replayQuizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: ""
                val token = intent.getStringExtra(EXTRA_UNLOCK_TOKEN) ?: ""
                if (!PremiumStore(this@QuizActivity).isPremium()) {
                    val ids = withContext(Dispatchers.IO) {
                        LastTestUnlockStore(this@QuizActivity).consumeUnlock(token, replayQuizId)
                    }
                    if (ids == null || ids.isEmpty()) {
                        startActivity(Intent(this@QuizActivity, HomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                        finish()
                        return@launch
                    }
                }
            }
            retryWrongMode = intent.getBooleanExtra(EXTRA_RETRY_WRONG, false)
        isRetryOfLockedQuiz = intent.getBooleanExtra(EXTRA_IS_RETRY, false)
        val retryAfterAd = intent.getBooleanExtra(EXTRA_RETRY_AFTER_AD, false)
        val retryUnlockToken = intent.getStringExtra(EXTRA_RETRY_UNLOCK_TOKEN)

            val blockedPkgForRetry = ""
            if (isRetryOfLockedQuiz && !retryAfterAd) {
                val policy = QuizRetryPolicy(this@QuizActivity)
                when (policy.getStartMode()) {
                    QuizRetryPolicy.StartMode.WAIT_COOLDOWN -> {
                        finish()
                        return@launch
                    }
                    else -> { }
                }
            }

            // Bypass prevention: non-premium retry requires valid one-shot token (from ad)
            if (isRetryOfLockedQuiz && !PremiumStore(this@QuizActivity).isPremium()) {
                if (retryUnlockToken.isNullOrBlank()) {
                    startActivity(Intent(this@QuizActivity, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                    return@launch
                }
                val failedQuizId = QuizRetryPolicy(this@QuizActivity).getSameTestToken()?.quizId ?: ""
                val consumed = withContext(Dispatchers.IO) {
                    RetryUnlockStore(this@QuizActivity).consumeRetryToken(retryUnlockToken, failedQuizId)
                }
                if (consumed == null || consumed.isEmpty()) {
                    startActivity(Intent(this@QuizActivity, HomeActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                    return@launch
                }
            }

            val isRemedial = intent.getBooleanExtra(EXTRA_REMEDIAL, false)
            quizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: UUID.randomUUID().toString()
            val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS)
            val replayQuestionIds = intent.getStringArrayListExtra(EXTRA_QUESTION_IDS_FOR_REPLAY)

            // EdumioOriginal: quiz access is open — no grade gate
            val levelGroup = LevelGroup.GRADE_5_8
            val effectiveGrade = 0
            val isLgsMode = false
            val bossLevel = 0
            val isGateMode = false

            val tapToRepoStart = System.currentTimeMillis()
            android.util.Log.d("QUIZ_PERF", "[QUIZ_PERF] tap_to_repo_start=${tapToRepoStart}ms (since epoch)")
            // STRICT EXAM ISOLATION: an EDUmio exam profile (IMAT/TIL-I/CEnT-S) is served ONLY from its own
            // verified bank — never legacy K-12/LGS/grade content, never the in-code fallback. Resolved
            // up-front so a slow/empty legacy build can never leak wrong-domain questions. Empty bank →
            // controlled error (handled below); no fallback.
            val isPracticeBuild = !isReplayFromLastTest && !retryWrongMode && (wrongIds == null || wrongIds.isEmpty())
            val activeExam: com.edumio.app.core.ExamType? = try {
                com.edumio.app.core.UserGoalPrefs(this@QuizActivity.applicationContext).getGoal().careerPath.examType
            } catch (_: Exception) { null }
            val result: QuizBuildResult =
                if (isPracticeBuild && activeExam != null && repo.isEdumioExam(activeExam)) {
                    withContext(Dispatchers.IO) {
                        val qs = repo.pickQuizForActiveExam(activeExam, count = targetCount, profileId = quizId)
                        QuizBuildResult(qs, null, "exam_${activeExam.name}", false, 0)
                    }
                } else (withTimeoutOrNull(5000L) {
                withContext(Dispatchers.IO) {
                buildQuizOnBackground(
                    context = this@QuizActivity.applicationContext,
                    repo = repo,
                    quizPrefs = quizPrefs,
                    isReplayFromLastTest = isReplayFromLastTest,
                    replayQuestionIds = replayQuestionIds,
                    bossLevel = bossLevel,
                    isGateMode = isGateMode,
                    isRemedial = isRemedial,
                    wrongIds = wrongIds,
                    retryWrongMode = retryWrongMode,
                    effectiveGrade = effectiveGrade,
                    levelGroup = levelGroup,
                    isLgsMode = isLgsMode,
                    quizId = quizId,
                    targetCount = targetCount,
                    intent = intent
                )
            }
            } ?: withContext(Dispatchers.IO) {
                buildQuizRelaxed(
                    repo = repo,
                    quizPrefs = quizPrefs,
                    isReplayFromLastTest = isReplayFromLastTest,
                    replayQuestionIds = replayQuestionIds,
                    isGateMode = isGateMode,
                    effectiveGrade = effectiveGrade,
                    levelGroup = levelGroup,
                    isLgsMode = isLgsMode,
                    quizId = quizId,
                    targetCount = targetCount
                )
            })
            val buildDoneMs = System.currentTimeMillis()
            android.util.Log.d("QUIZ_PERF", "[QUIZ_PERF] total_build_ms=${buildDoneMs - tapToRepoStart} picker=${result.pickerDebugPath} questions=${result.questions.size}")
            withContext(Dispatchers.Main) {
                b.loadingBar.visibility = View.GONE
                if (result.questions.isEmpty()) {
                    // Controlled empty-pool error — NEVER a fake question or a wrong-domain fallback.
                    showEmptyPoolError()
                    return@withContext
                }
                // Loading finished — reveal the real question UI (hidden during load).
                b.questionCard.visibility = View.VISIBLE
                b.subjectChip.visibility = View.VISIBLE
                b.progressText.visibility = View.VISIBLE
                b.optionsGroup.visibility = View.VISIBLE
                b.nextBtn.visibility = View.VISIBLE
                questions = result.questions
                savedInstanceState?.getInt("quiz_index", -1)?.takeIf { it >= 0 && questions.isNotEmpty() }?.let { saved ->
                    index = saved.coerceIn(0, questions.lastIndex)
                }
                poolDebug = result.poolDebug
                pickerDebugPath = result.pickerDebugPath
                debugWrongUsed = result.debugWrongUsed

                b.subjectChip.text = getString(R.string.quiz_subject_label)
                b.nextBtn.isEnabled = true
                b.nextBtn.setOnClickListener { goNext() }

                b.submitBtn.visibility = View.GONE
                isLgsModeForDebug = isLgsMode
                applyQuizResultAndRender(result, effectiveGrade, isLgsMode, targetCount)

                val firstQuestionMs = System.currentTimeMillis()
                android.util.Log.d("QUIZ_PERF", "[QUIZ_PERF] total_start_to_first_question=${firstQuestionMs - tapToRepoStart}ms questions=${questions.size}")

                // [PERF FIX] Deferred: compute pool debug stats AFTER first question renders.
                // Removes ~10 extra DB queries from the 5s build window.
                if (!isLgsMode && effectiveGrade in 1..7) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val stats = repo.buildPoolDebugStatsForGrade(effectiveGrade, quizPrefs.difficulty())
                            withContext(Dispatchers.Main) { poolDebug = stats }
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

    private data class QuizBuildResult(
        val questions: List<Question>,
        val poolDebug: QuestionRepository.PoolDebugForGrade?,
        val pickerDebugPath: String,
        val remedialFallbackWarning: Boolean,
        val debugWrongUsed: Int
    )

    private fun buildQuizOnBackground(
        context: android.content.Context,
        repo: QuestionRepository,
        quizPrefs: QuizPrefs,
        isReplayFromLastTest: Boolean,
        replayQuestionIds: ArrayList<String>?,
        bossLevel: Int,
        isGateMode: Boolean,
        isRemedial: Boolean,
        wrongIds: ArrayList<String>?,
        retryWrongMode: Boolean,
        effectiveGrade: Int,
        levelGroup: LevelGroup,
        isLgsMode: Boolean,
        quizId: String,
        targetCount: Int,
        intent: Intent
    ): QuizBuildResult {
        // IMAT mode: when the active study area is an IMAT career, serve ONLY the isolated
        // official IMAT pool. Replay / retry-wrong keep their existing ID-based paths so the
        // wrong-answer review still works across exams.
        val imatMode = try {
            com.edumio.app.core.UserGoalPrefs(context).getGoal().careerPath.examType == com.edumio.app.core.ExamType.IMAT
        } catch (_: Exception) { false }
        if (imatMode && !isReplayFromLastTest && !retryWrongMode && (wrongIds == null || wrongIds.isEmpty())) {
            val imat = repo.pickQuizQuestionsForImat(count = targetCount)
            if (imat.isNotEmpty()) {
                return QuizBuildResult(
                    questions = imat,
                    poolDebug = null,
                    pickerDebugPath = "imat",
                    remedialFallbackWarning = false,
                    debugWrongUsed = 0
                )
            }
        }
        val scheduler = WrongQuestionScheduler(context)
        // [PERF FIX] buildPoolDebugStatsForGrade runs ~10 extra DB queries.
        // Removed from the 5-second build window — deferred to after first question renders.
        val poolDebug: QuestionRepository.PoolDebugForGrade? = null

        var pickerPath = ""
        var remedialWarning = false
        var wrongUsed = 0
        var injectedDueId: String? = null

        val q = when {
            isLgsMode && !(isReplayFromLastTest && replayQuestionIds != null && replayQuestionIds.size >= targetCount) -> {
                pickerPath = "LGS"
                val dueId = scheduler.getDueWrongQuestion()
                val dueQ = if (dueId != null) repo.getQuestionById(dueId) else null
                if (dueQ != null && dueId != null) {
                    val rest = repo.pickQuizQuestionsForLGS(targetCount, quizId, excludeIds = setOf(dueId))
                    injectedDueId = dueId
                    (rest.dropLast(1) + dueQ).shuffled()
                } else {
                    repo.pickQuizQuestionsForLGS(targetCount, quizId)
                }
            }
            isReplayFromLastTest && replayQuestionIds != null && replayQuestionIds.size >= targetCount -> {
                pickerPath = "REPLAY"
                val all = repo.loadAllQuestions().associateBy { it.id }
                replayQuestionIds.mapNotNull { all[it] }
            }
            bossLevel > 0 -> {
                pickerPath = if (isLgsMode) "BOSS_LGS" else if (effectiveGrade in 1..7) "BOSS_GRADE" else "BOSS"
                when {
                    isLgsMode -> repo.pickQuizQuestionsForLGS(targetCount, quizId)
                    effectiveGrade in 1..7 -> repo.pickBossQuestionsByGrade(effectiveGrade, targetCount)
                    else -> repo.pickBossQuestions(levelGroup, targetCount)
                }
            }
            isGateMode -> {
                val excludeFailed = emptySet<String>()
                pickerPath = when {
                    isLgsMode -> "GATE_LGS"
                    effectiveGrade in 1..7 -> "GATE_GRADE"
                    else -> "GATE"
                }
                when {
                    isLgsMode -> repo.pickQuizQuestionsForLGS(targetCount, quizId, excludeIds = excludeFailed)
                    effectiveGrade in 1..7 -> repo.pickGateQuestionsByGrade(effectiveGrade, targetCount, excludeIds = excludeFailed)
                    else -> repo.pickGateQuestions(levelGroup, targetCount, excludeIds = excludeFailed)
                }
            }
            isRemedial -> when {
                isLgsMode -> {
                    pickerPath = "REMEDIAL_LGS"
                    repo.pickQuizQuestionsForLGS(targetCount, quizId)
                }
                effectiveGrade in 1..7 -> {
                    pickerPath = "REMEDIAL_GRADE"
                    val (list, usedFallback) = repo.pickRemedialQuestionsByGrade(effectiveGrade, targetCount, emptyList())
                    remedialWarning = usedFallback
                    list
                }
                else -> {
                    pickerPath = "REMEDIAL"
                    val (list, usedFallback) = repo.pickRemedialQuestions(levelGroup, targetCount, emptyList())
                    remedialWarning = usedFallback
                    list
                }
            }
            wrongIds != null && wrongIds.isNotEmpty() -> {
                val all = repo.loadAllQuestions().associateBy { it.id }
                val found = wrongIds.mapNotNull { all[it] }
                val preferredWrongIds = found.map { it.id }.toSet()
                when {
                    isLgsMode -> {
                        pickerPath = "WRONG_ONLY_LGS"
                        repo.pickQuizQuestionsForLGS(targetCount, quizId)
                    }
                    effectiveGrade in 1..7 -> {
                        pickerPath = "WRONG_ONLY"
                        val picked = repo.pickQuizQuestionsByGrade(effectiveGrade, targetCount, quizId, preferredWrongIds = preferredWrongIds)
                        wrongUsed = picked.count { it.id in preferredWrongIds }
                        picked
                    }
                    else -> {
                        pickerPath = "WRONG_ONLY_SUBJECT"
                        val base = if (found.isNotEmpty()) found.shuffled().take(targetCount) else emptyList()
                        if (base.size < targetCount) {
                            repo.pickQuizQuestions(levelGroup, targetCount, quizPrefs.difficulty(), quizPrefs.selectedCategories(), quizId)
                        } else base
                    }
                }
            }
            retryWrongMode -> when {
                isLgsMode -> {
                    pickerPath = "WRONG_ONLY_LGS"
                    repo.pickQuizQuestionsForLGS(targetCount, quizId)
                }
                effectiveGrade in 1..7 -> {
                    pickerPath = "WRONG_ONLY"
                    val wrong = repo.pickRetryWrongQuestionsByGrade(effectiveGrade)
                    val preferredWrongIds = wrong.map { it.id }.toSet()
                    val picked = repo.pickQuizQuestionsByGrade(effectiveGrade, targetCount, quizId, preferredWrongIds = preferredWrongIds)
                    wrongUsed = picked.count { it.id in preferredWrongIds }
                    picked
                }
                else -> {
                    pickerPath = "WRONG_ONLY_SUBJECT"
                    val wrong = repo.pickRetryWrongQuestions(levelGroup)
                    if (wrong.size < targetCount) repo.pickQuizQuestions(levelGroup, targetCount, quizPrefs.difficulty(), quizPrefs.selectedCategories(), quizId)
                    else wrong.shuffled().take(targetCount)
                }
            }
            else -> {
                val dueId = scheduler.getDueWrongQuestion()
                val dueQ = if (dueId != null) repo.getQuestionById(dueId) else null
                when {
                    isLgsMode -> {
                        pickerPath = "LGS"
                        if (dueQ != null && dueId != null) {
                            val rest = repo.pickQuizQuestionsForLGS(targetCount, quizId, excludeIds = setOf(dueId))
                            injectedDueId = dueId
                            (rest.dropLast(1) + dueQ).shuffled()
                        } else {
                            repo.pickQuizQuestionsForLGS(targetCount, quizId)
                        }
                    }
                    effectiveGrade in 1..7 -> {
                        pickerPath = "GRADE"
                        if (dueQ != null && dueId != null) {
                            val rest = repo.pickQuizQuestionsByGrade(effectiveGrade, targetCount, quizId, excludeIds = setOf(dueId))
                            injectedDueId = dueId
                            (rest.dropLast(1) + dueQ).shuffled()
                        } else {
                            repo.pickQuizQuestionsByGrade(effectiveGrade, targetCount, quizId)
                        }
                    }
                    else -> {
                        pickerPath = "SUBJECT_ONLY"
                        val subjectFilter = intent.getStringExtra(EXTRA_SUBJECT_FILTER)?.trim()?.takeIf { it.isNotEmpty() }
                        // Daily-mission may pass a curated category set (e.g. premium weak-biased).
                        val missionCategories = intent.getStringExtra(EXTRA_MISSION_CATEGORIES)
                            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                            ?.takeIf { it.isNotEmpty() }
                        val categories = subjectFilter?.let { tr ->
                            Subject.entries.find { it.tr == tr }?.let { setOf(it.name) }
                        } ?: missionCategories ?: quizPrefs.selectedCategories()
                        val base = repo.pickQuizQuestions(levelGroup, targetCount, quizPrefs.difficulty(), categories, quizId)
                        if (dueQ != null && dueId != null && dueId !in base.map { it.id }) {
                            injectedDueId = dueId
                            (base.dropLast(1) + dueQ).shuffled()
                        } else {
                            base
                        }
                    }
                }
            }
        }
        injectedDueId?.let { scheduler.markShown(it) }

        // --- Distribution verification & anti-clustering ---
        val subjectHistogram = q.groupingBy { it.subject.tr }.eachCount()
        android.util.Log.d(
            "TestBuilder",
            "[GRADE_FINAL_DISTRIBUTION] pickerPath=$pickerPath grade=$effectiveGrade total=${q.size} | " +
                subjectHistogram.entries.joinToString(" | ") { "${it.key}=${it.value}" }
        )

        // Anti-clustering: reorder to minimize consecutive same-subject runs.
        // Only for grade mode (multi-subject tests). Preserves randomness within constraint.
        val reordered = if (pickerPath == "GRADE" && q.size > 1) {
            spreadSubjects(q)
        } else {
            q
        }

        val preUiHistogram = reordered.groupingBy { it.subject.tr }.eachCount()
        android.util.Log.d(
            "TestBuilder",
            "[GRADE_PRE_UI_DISTRIBUTION] total=${reordered.size} | " +
                preUiHistogram.entries.joinToString(" | ") { "${it.key}=${it.value}" } +
                " | order=" + reordered.take(20).joinToString(",") { it.subject.name.take(3) }
        )

        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "TestBuilder",
                "quizId=$quizId pickerPath=$pickerPath total=${reordered.size} injectedDueId=$injectedDueId"
            )
        }
        return QuizBuildResult(reordered, poolDebug, pickerPath, remedialWarning, wrongUsed)
    }

    /**
     * Reorder questions so that consecutive same-subject runs are minimized.
     * Uses a greedy interleave: in each slot, pick the question whose subject
     * differs from the previous one (prefer the subject with the most remaining).
     * Preserves all questions — no additions or removals.
     */
    private fun spreadSubjects(questions: List<Question>): List<Question> {
        if (questions.size <= 2) return questions.shuffled()
        // Group by subject, shuffle within each group for internal randomness.
        val bySubject = questions.groupBy { it.subject }
            .mapValues { (_, v) -> v.shuffled().toMutableList() }
            .toMutableMap()
        val result = mutableListOf<Question>()
        var prevSubject: Subject? = null

        repeat(questions.size) {
            // Pick from a subject different from the previous one, preferring the largest bucket.
            val candidate = bySubject.entries
                .filter { it.value.isNotEmpty() && it.key != prevSubject }
                .maxByOrNull { it.value.size }
            // If all remaining are same subject, allow consecutive.
            val entry = candidate ?: bySubject.entries.firstOrNull { it.value.isNotEmpty() }
                ?: return@repeat
            // removeAt(0), not removeFirst(): on API 35 / Java 21 the latter resolves to
            // java.util.SequencedCollection.removeFirst(), which does not exist below API 35.
            val q = entry.value.removeAt(0)
            result.add(q)
            prevSubject = q.subject
            if (entry.value.isEmpty()) bySubject.remove(entry.key)
        }
        return result
    }

    /** Fast fallback when build times out (>5s). Uses simpler pool+shuffle logic. */
    private fun buildQuizRelaxed(
        repo: QuestionRepository,
        quizPrefs: QuizPrefs,
        isReplayFromLastTest: Boolean,
        replayQuestionIds: ArrayList<String>?,
        isGateMode: Boolean,
        effectiveGrade: Int,
        levelGroup: LevelGroup,
        isLgsMode: Boolean,
        quizId: String,
        targetCount: Int
    ): QuizBuildResult {
        val excludeFailed = emptySet<String>()
        val q = when {
            isReplayFromLastTest && replayQuestionIds != null && replayQuestionIds.size >= targetCount -> {
                val all = repo.loadAllQuestions().associateBy { it.id }
                replayQuestionIds.mapNotNull { all[it] }
            }
            isGateMode -> when {
                isLgsMode -> repo.pickQuizQuestionsForLGS(targetCount, quizId, excludeIds = excludeFailed)
                effectiveGrade in 1..7 -> repo.pickQuizQuestionsRelaxedByGrade(effectiveGrade, targetCount, quizId, excludeIds = excludeFailed)
                else -> repo.pickQuizQuestionsRelaxed(levelGroup, targetCount, quizPrefs.difficulty(), quizPrefs.selectedCategories(), quizId, excludeIds = excludeFailed)
            }
            isLgsMode -> repo.pickQuizQuestionsForLGS(targetCount, quizId)
            effectiveGrade in 1..7 -> repo.pickQuizQuestionsRelaxedByGrade(effectiveGrade, targetCount, quizId)
            else -> repo.pickQuizQuestionsRelaxed(levelGroup, targetCount, quizPrefs.difficulty(), quizPrefs.selectedCategories(), quizId)
        }
        return QuizBuildResult(q, null, "RELAXED", false, 0)
    }

    private fun applyQuizResultAndRender(result: QuizBuildResult, effectiveGrade: Int, isLgsMode: Boolean = false, requiredCount: Int = QuestionRepository.MIN_QUESTIONS_PER_TEST) {
        val available = questions.size
        val hardFail = available < ABSOLUTE_MIN_QUESTIONS

        android.util.Log.w("QuizActivity", "[QUIZ_RENDER] available=$available required=$requiredCount hardFail=$hardFail mode=${result.pickerDebugPath}")

        if (hardFail) {
            // Truly insufficient — cannot build any meaningful quiz.
            b.subjectChip.text = getString(R.string.quiz_pool_insufficient)
            val msg = if (questions.isEmpty()) {
                if (retryWrongMode) getString(R.string.quiz_no_wrong_answers)
                else getString(R.string.quiz_pool_insufficient_detail, requiredCount)
            } else getString(R.string.quiz_pool_limited_detail, questions.size, requiredCount)
            b.questionText.text = msg
            b.optionsGroup.visibility = View.GONE
            b.feedbackText.visibility = View.GONE
            b.optA.visibility = View.GONE
            b.optB.visibility = View.GONE
            b.optC.visibility = View.GONE
            b.optD.visibility = View.GONE
            b.optE.visibility = View.GONE
            b.nextBtn.isEnabled = true
            b.nextBtn.text = getString(R.string.quiz_go_home)
            b.nextBtn.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        } else {
            // Serve whatever we have — notify user if it's a shorter quiz.
            if (available < requiredCount) {
                android.widget.Toast.makeText(
                    this,
                    getString(R.string.quiz_shorter_test_notice, available),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                android.util.Log.w("QuizActivity", "[QUIZ_DEGRADED] Serving $available/$requiredCount questions (soft fallback)")
            }
            if (result.remedialFallbackWarning) {
                android.widget.Toast.makeText(this, getString(R.string.quiz_pool_limited_warning), android.widget.Toast.LENGTH_LONG).show()
            }
            b.optionsGroup.visibility = View.VISIBLE
            b.optA.visibility = View.VISIBLE
            b.optB.visibility = View.VISIBLE
            b.optC.visibility = View.VISIBLE
            b.optD.visibility = View.VISIBLE
            render()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("quiz_index", index)
        outState.putString("quiz_id", quizId)
        outState.putLong("quiz_started", startedAt)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) return true
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Stable per-question display permutation. order[displayPos] = originalIndex.
     * Identity (no shuffle) when runtime shuffle is off, the question is not IMAT, or the options
     * are the literal letters A–E (image-option questions whose letters index into one figure).
     */
    private fun orderFor(q: Question, n: Int): IntArray {
        optionOrders[q.id]?.let { if (it.size == n) return it }
        val identity = IntArray(n) { it }
        val isLetterOptions = (0 until n).all { q.choices.getOrNull(it)?.trim() == ('A' + it).toString() }
        val doShuffle = SHUFFLE_OPTIONS_AT_RUNTIME && q.examType.isImatFormat() && n >= 2 && !isLetterOptions
        val order = if (doShuffle) identity.toMutableList().apply { shuffle() }.toIntArray() else identity
        optionOrders[q.id] = order
        return order
    }

    private fun render() {
        if (questions.isEmpty()) return
        index = index.coerceIn(0, questions.size - 1)
        val q = questions[index]

        b.progressText.text = "${index + 1}/${questions.size}"
        // IMAT-format questions show their exam subject label (carried in topic); no Turkish grade chip.
        b.subjectChip.text = if (q.examType.isImatFormat()) {
            q.topic?.takeIf { it.isNotBlank() } ?: q.examType.displayName
        } else {
            "${q.subject.tr} • ${q.gradeDisplayLabel}"
        }
        b.questionText.text = QuestionTypography.format(q.stem)

        if (!q.imageAsset.isNullOrBlank()) {
            val path = q.imageAsset!!.trim()
            try {
                assets.open(path).use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        b.questionImage.setImageBitmap(bitmap)
                        b.questionImage.visibility = View.VISIBLE
                    } else {
                        b.questionImage.visibility = View.GONE
                    }
                }
            } catch (_: Exception) {
                b.questionImage.visibility = View.GONE
            }
        } else {
            b.questionImage.visibility = View.GONE
        }

        // IMAT-format: official A–E choices are used verbatim — skip the K-12 output guard, which is
        // tuned for 4-option content and can drop option E. Other exams keep the full pipeline.
        val displayChoices = if (q.examType.isImatFormat()) {
            q.choices
        } else {
            // Layer 1: QuizOutputGuard sanitizes presentationChoices (dedup, blank fill)
            // Layer 2: q.choices was already sanitized by QuestionMapper.toQuestion().
            QuizOutputGuard.sanitizeQuestion(q).presentationChoices ?: q.choices
        }
        // Apply the runtime display permutation (see orderFor). Non-blank options are reordered;
        // any trailing blank stays put. Everything stored/scored still uses the ORIGINAL index.
        val nOpts = displayChoices.count { !it.isNullOrBlank() }.coerceAtLeast(1)
        val order = orderFor(q, nOpts)
        val shown = ArrayList<String>(displayChoices.size)
        for (i in 0 until nOpts) shown.add(displayChoices.getOrNull(order[i]) ?: "-")
        for (i in nOpts until displayChoices.size) shown.add(displayChoices.getOrNull(i) ?: "-")

        b.optA.text = shown.getOrNull(0) ?: "-"
        b.optB.text = shown.getOrNull(1) ?: "-"
        b.optC.text = shown.getOrNull(2) ?: "-"
        b.optD.text = shown.getOrNull(3) ?: "-"
        // 5th option (A–E exams like IMAT). Hidden when the question has only 4 choices.
        val hasFifth = shown.size >= 5 && !shown[4].isNullOrBlank()
        b.optE.visibility = if (hasFifth) View.VISIBLE else View.GONE
        if (hasFifth) b.optE.text = shown[4]

        b.optionsGroup.setOnCheckedChangeListener(null)
        val saved = answers[q.id] ?: -1
        // `saved` is the ORIGINAL option index; map it to its current display position.
        val savedDisplay = if (saved in 0 until nOpts) order.indexOf(saved) else -1
        when (savedDisplay) {
            0 -> b.optA.isChecked = true
            1 -> b.optB.isChecked = true
            2 -> b.optC.isChecked = true
            3 -> b.optD.isChecked = true
            4 -> b.optE.isChecked = true
            else -> b.optionsGroup.clearCheck()
        }
        b.optionsGroup.setOnCheckedChangeListener { _, checkedId ->
            val displayPos = when (checkedId) {
                b.optA.id -> 0
                b.optB.id -> 1
                b.optC.id -> 2
                b.optD.id -> 3
                b.optE.id -> 4
                else -> -1
            }
            if (displayPos >= 0) {
                com.edumio.app.ui.Interactions.lightTick(b.optionsGroup)
                // Store the ORIGINAL (official) option index, not the shuffled display position.
                answers[q.id] = if (displayPos < nOpts) order[displayPos] else displayPos
                if (index == questions.size - 1) {
                    // Auto-submit on last question - no Finish Test button
                    b.nextBtn.postDelayed({ if (!isFinishing) finishTest() }, 600)
                }
            }
        }

        b.feedbackText.visibility = View.GONE
        listOf(b.optA, b.optB, b.optC, b.optD, b.optE).forEach {
            it.alpha = 1f
            // Restore the shared state-list background (NOT a flat colour): it carries the option's
            // card look AND its checked state, so the tapped answer stays visibly selected.
            it.setBackgroundResource(R.drawable.bg_choice_button)
        }

        b.nextBtn.isEnabled = true
        b.nextBtn.text = if (index < questions.size - 1) "Sonraki Soru →" else "Gönder"
        b.nextBtn.visibility = View.VISIBLE
    }

    private fun goNext() {
        saveCurrentSelection()
        if (index < questions.size - 1) {
            index++
            render()
        } else {
            finishTest()
        }
    }

    private fun saveCurrentSelection() {
        val q = questions.getOrNull(index) ?: return
        val displayPos = when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0
            b.optB.id -> 1
            b.optC.id -> 2
            b.optD.id -> 3
            b.optE.id -> 4
            else -> -1
        }
        if (displayPos < 0) return
        // Map the shuffled display position back to the ORIGINAL (official) option index.
        val order = optionOrders[q.id]
        answers[q.id] = if (order != null && displayPos < order.size) order[displayPos] else displayPos
    }

    private fun finishTest() {
        if (isFinishing) return
        isFinishing = true
        saveCurrentSelection()

        var correctCount = 0
        var wrongCount = 0
        val wrongIds = mutableListOf<String>()
        val answerRecords = mutableListOf<AnswerRecord>()

        for (q in questions) {
            val sel = answers[q.id] ?: -1
            when {
                sel < 0 -> { }
                sel == q.correctIndex -> {
                    correctCount++
                    answerRecords.add(AnswerRecord(q.id, sel, q.correctIndex))
                }
                else -> {
                    wrongCount++
                    wrongIds.add(q.id)
                    answerRecords.add(AnswerRecord(q.id, sel, q.correctIndex))
                }
            }
        }
        val blankCount = questions.size - correctCount - wrongCount

        val questionsMap = questions.associateBy { it.id }
        val bySubject = questions.groupBy {
            if (it.examType.isImatFormat()) (it.topic?.takeIf { t -> t.isNotBlank() } ?: it.examType.displayName) else it.subject.tr
        }.mapValues { (_, qs) -> qs.size }
        val breakdown = bySubject.entries.joinToString(", ") { "${it.key}: ${it.value}" }.takeIf { it.isNotBlank() }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repo.recordAnswers(answerRecords, questionsMap, quizId)
                repo.onQuizCompleted(questions.map { it.id })
                repo.insertSnapshot(quizId, correctCount, questions.size, questions.map { it.id }, answers.toMap(), wrongIds, breakdown)
                val scheduler = WrongQuestionScheduler(this@QuizActivity.applicationContext)
                wrongIds.forEach { scheduler.registerWrong(it) }
                answerRecords.filter { it.isCorrect }.forEach { scheduler.markCorrect(it.questionId) }
                scheduler.onTestCompleted()
                // Daily mission progress: count this test and any corrected wrongs.
                val mission = com.edumio.app.core.DailyMissionManager(this@QuizActivity.applicationContext)
                mission.onTestCompleted()
                if (wrongIds.isNotEmpty()) {
                    val correctedWrongCount = answerRecords.count { it.isCorrect && it.questionId in wrongIds }
                    repeat(correctedWrongCount.coerceAtLeast(0)) {
                        mission.onRetrySolved()
                    }
                }
            }
            val isGateMode = false
            val isRemedial = intent.getBooleanExtra(EXTRA_REMEDIAL, false)
            val total = questions.size
            val accuracy = if (total > 0) correctCount.toFloat() / total else 0f
            val minPct = 60
            val pctScore = if (total > 0) (100f * correctCount / total) else 0f
            val passed = when {
                isGateMode || isRetryOfLockedQuiz || isRemedial ->
                    pctScore >= minPct
                else -> accuracy >= 0.6f
            }
            val completedAt = System.currentTimeMillis()
            val session = QuizSession(
                quizId = quizId,
                startedAt = if (startedAt > 0L) startedAt else completedAt - 60000,
                questionIds = questions.map { it.id },
                answers = answers.toMap(),
                completedAt = completedAt,
                correctCount = correctCount,
                wrongCount = wrongCount,
                blankCount = blankCount,
                passed = passed,
                wrongQuestionIds = wrongIds
            )
            val blockedPkg = ""
            if (passed && (isGateMode || isRetryOfLockedQuiz)) {
                com.edumio.app.core.QuizRetryPolicy(this@QuizActivity).onPass()
            }
            if (!passed && (isGateMode || isRetryOfLockedQuiz) && !isRemedial) {
                com.edumio.app.core.QuizRetryPolicy(this@QuizActivity).onFail(
                    this@QuizActivity,
                    com.edumio.app.core.QuizRetryPolicy.SameTestToken(quizId, questions.map { it.id })
                )
            }
            startActivity(Intent(this@QuizActivity, QuizResultActivity::class.java).apply {
                putExtra(QuizResultActivity.EXTRA_SESSION, QuizResultActivity.encodeSession(session))
                putExtra(QuizResultActivity.EXTRA_QUESTIONS_JSON, QuizResultActivity.encodeQuestions(questions))
                putExtra(QuizResultActivity.EXTRA_IS_RETRY, isRetryOfLockedQuiz)
                putExtra(QuizResultActivity.EXTRA_IS_GATE_MODE, isGateMode)
                putExtra(QuizResultActivity.EXTRA_BLOCKED_PACKAGE, blockedPkg)
            })
            finish()
        }
    }
}
