package com.brainbuddy.app.quiz

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brainbuddy.app.R
import com.brainbuddy.app.BuildConfig
import com.brainbuddy.app.core.GradePrefs
import com.brainbuddy.app.core.LevelMode
import com.brainbuddy.app.core.LastTestUnlockStore
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.RetryUnlockStore
import com.brainbuddy.app.core.QuizRetryPolicy
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.databinding.ActivityQuizBinding
import com.brainbuddy.app.quiz.LevelGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ui.MainActivity
import java.util.UUID

class QuizActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RETRY_WRONG = "retry_wrong"
        const val EXTRA_WRONG_IDS = "wrong_ids"
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_IS_RETRY = "is_retry"
        const val EXTRA_QUESTIONS_JSON = "questions_json"
        const val EXTRA_GATE_MODE = "gate_mode"
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_REMEDIAL = "remedial"
        const val EXTRA_BOSS_LEVEL = "boss_level"
        const val EXTRA_REPLAY_FROM_LAST_TEST = "replay_from_last_test"
        const val EXTRA_UNLOCK_TOKEN = "unlock_token"
        const val EXTRA_QUESTION_IDS_FOR_REPLAY = "question_ids_for_replay"
        const val EXTRA_RETRY_AFTER_AD = "retry_after_ad"
        const val EXTRA_RETRY_UNLOCK_TOKEN = "retry_unlock_token"
        const val EXTRA_SUBJECT_FILTER = "subject_filter"
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

    private val answers = mutableMapOf<String, Int>()
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

        if (BuildConfig.DEBUG) {
            val gp = GradePrefs(this)
            val modeStr = if (gp.getSelectedMode() == LevelMode.LGS) "LGS" else "GRADE"
            val gradeStr = if (modeStr == "GRADE" && gp.getSelectedGrade() in 1..7) " grade=${gp.getSelectedGrade()}" else ""
            val debugLine = "DEBUG_PICKER_OK mode=$modeStr$gradeStr"
            b.debugPickerText.text = debugLine
            b.debugPickerText.visibility = View.VISIBLE

            val isAttached = b.debugPickerText.parent != null
            val textOk = b.debugPickerText.text?.toString()?.contains("DEBUG_PICKER_OK") == true
            if (!isAttached || !textOk) {
                throw RuntimeException("DEBUG LINE NOT WIRED")
            }
        }

        try {
            initQuiz(savedInstanceState)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("QuizActivity", "OOM", e)
            android.widget.Toast.makeText(this, "Bellek yetersiz. Uygulamayı yeniden başlatın.", android.widget.Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            android.util.Log.e("QuizActivity", "init error", e)
            finish()
        }
    }


    private fun initQuiz(savedInstanceState: Bundle?) {
        repo = QuestionRepository(this)
        quizPrefs = QuizPrefs(this)

        // Show loading UI immediately - keeps "Test hazırlanıyor..." responsive
        b.subjectChip.text = getString(com.brainbuddy.app.R.string.test_preparing)
        b.nextBtn.isEnabled = false
        b.questionText.text = getString(com.brainbuddy.app.R.string.test_preparing)
        b.optionsGroup.visibility = View.GONE
        b.submitBtn.visibility = View.GONE
        b.nextBtn.setOnClickListener { }

        lifecycleScope.launch {
            val protectionPrefs = ProtectionPrefs(this@QuizActivity)
            val isReplayFromLastTest = intent.getBooleanExtra(EXTRA_REPLAY_FROM_LAST_TEST, false)
            if (isReplayFromLastTest) {
                val replayQuizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: ""
                val token = intent.getStringExtra(EXTRA_UNLOCK_TOKEN) ?: ""
                if (!PremiumStore(this@QuizActivity).isPremium()) {
                    val ids = withContext(Dispatchers.IO) {
                        LastTestUnlockStore(this@QuizActivity).consumeUnlock(token, replayQuizId)
                    }
                    if (ids == null || ids.isEmpty()) {
                        startActivity(Intent(this@QuizActivity, com.brainbuddy.app.ui.AdLimitReachedActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra(com.brainbuddy.app.ui.AdLimitReachedActivity.EXTRA_TITLE, getString(com.brainbuddy.app.R.string.ad_limit_reached_bypass_title))
                            putExtra(com.brainbuddy.app.ui.AdLimitReachedActivity.EXTRA_MESSAGE, getString(com.brainbuddy.app.R.string.ad_limit_reached_bypass_message))
                        })
                        finish()
                        return@launch
                    }
                }
            }
            retryWrongMode = intent.getBooleanExtra(EXTRA_RETRY_WRONG, false)
        isRetryOfLockedQuiz = intent.getBooleanExtra(EXTRA_IS_RETRY, false)
        val retryAfterAd = intent.getBooleanExtra(EXTRA_RETRY_AFTER_AD, false)
        val retryUnlockToken = intent.getStringExtra(EXTRA_RETRY_UNLOCK_TOKEN)

            val blockedPkgForRetry = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.trim().orEmpty()
            if (isRetryOfLockedQuiz && !retryAfterAd && protectionPrefs.userLocked()) {
                val policy = QuizRetryPolicy(this@QuizActivity)
                when (policy.getStartMode()) {
                QuizRetryPolicy.StartMode.REQUIRE_AD -> {
                    val qId = protectionPrefs.lastFailedQuizId()
                    val qIds = protectionPrefs.lastFailedQuestionIds()
                    if (qIds.size >= QuestionRepository.MIN_QUESTIONS_PER_TEST) {
                        startActivity(Intent(this@QuizActivity, QuizRetryAdActivity::class.java).apply {
                            putExtra(QuizRetryAdActivity.EXTRA_QUIZ_ID, qId)
                            putStringArrayListExtra(QuizRetryAdActivity.EXTRA_QUESTION_IDS, java.util.ArrayList(qIds))
                            putExtra(QuizRetryAdActivity.EXTRA_BLOCKED_PACKAGE, blockedPkgForRetry)
                        })
                    }
                    finish()
                    return@launch
                }
                QuizRetryPolicy.StartMode.WAIT_COOLDOWN -> {
                    val qId = protectionPrefs.lastFailedQuizId()
                    val qIds = protectionPrefs.lastFailedQuestionIds()
                    if (qIds.size >= QuestionRepository.MIN_QUESTIONS_PER_TEST) {
                        startActivity(Intent(this@QuizActivity, QuizCooldownActivity::class.java).apply {
                            putExtra(QuizCooldownActivity.EXTRA_QUIZ_ID, qId)
                            putStringArrayListExtra(QuizCooldownActivity.EXTRA_QUESTION_IDS, java.util.ArrayList(qIds))
                            putExtra(QuizCooldownActivity.EXTRA_BLOCKED_PACKAGE, blockedPkgForRetry)
                        })
                    }
                    finish()
                    return@launch
                }
                else -> { }
            }
        }

            // Bypass prevention: non-premium retry requires valid one-shot token (from ad or cooldown)
            if (isRetryOfLockedQuiz && protectionPrefs.userLocked() && !PremiumStore(this@QuizActivity).isPremium()) {
                if (retryUnlockToken.isNullOrBlank()) {
                    startActivity(Intent(this@QuizActivity, com.brainbuddy.app.LockScreenActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                    return@launch
                }
                val consumed = withContext(Dispatchers.IO) {
                    RetryUnlockStore(this@QuizActivity).consumeRetryToken(retryUnlockToken, protectionPrefs.lastFailedQuizId())
                }
                if (consumed == null || consumed.isEmpty()) {
                    startActivity(Intent(this@QuizActivity, com.brainbuddy.app.LockScreenActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                    return@launch
                }
            }

            val isRemedial = intent.getBooleanExtra(EXTRA_REMEDIAL, false)
            quizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: UUID.randomUUID().toString()
            val wrongIds = intent.getStringArrayListExtra(EXTRA_WRONG_IDS)
            val replayQuestionIds = intent.getStringArrayListExtra(EXTRA_QUESTION_IDS_FOR_REPLAY)

            val (levelGroup, effectiveGrade, isLgsMode) = withContext(Dispatchers.IO) {
                val gp = GradePrefs(this@QuizActivity)
                val lg = repo.getLevelGroupFromPrefs()
                val mode = gp.getSelectedMode()
                val lgs = mode == LevelMode.LGS
                val eff = if (!lgs && gp.hasGradeSelected()) gp.getEffectiveGradeForQuiz() else 0
                Triple(lg, eff, lgs)
            }
            val gradePrefs = GradePrefs(this@QuizActivity)
            val needsLevel = !(isReplayFromLastTest && replayQuestionIds != null && replayQuestionIds.size >= QuestionRepository.MIN_QUESTIONS_PER_TEST)
            if (needsLevel && !gradePrefs.hasLevelSelected()) {
                android.widget.Toast.makeText(this@QuizActivity, com.brainbuddy.app.R.string.grade_required_toast, android.widget.Toast.LENGTH_LONG).show()
                // Navigate to TestSettings within the existing task so Back returns to the previous screen,
                // not to the launcher. Do not use NEW_TASK here.
                startActivity(Intent(this@QuizActivity, com.brainbuddy.app.ui.TestSettingsActivity::class.java))
                finish()
                return@launch
            }
            val bossLevel = intent.getIntExtra(EXTRA_BOSS_LEVEL, -1)
            val isGateMode = intent.getBooleanExtra(EXTRA_GATE_MODE, false)
            val targetCount = QuestionRepository.MIN_QUESTIONS_PER_TEST

            val result = withTimeoutOrNull(5000L) {
                withContext(Dispatchers.IO) {
                buildQuizOnBackground(
                    context = this@QuizActivity.applicationContext,
                    repo = repo,
                    quizPrefs = quizPrefs,
                    protectionPrefs = protectionPrefs,
                    isReplayFromLastTest = isReplayFromLastTest,
                    replayQuestionIds = replayQuestionIds,
                    bossLevel = bossLevel,
                    isGateMode = isGateMode,
                    isRetryOfLockedQuiz = isRetryOfLockedQuiz,
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
                    protectionPrefs = protectionPrefs,
                    isReplayFromLastTest = isReplayFromLastTest,
                    replayQuestionIds = replayQuestionIds,
                    bossLevel = bossLevel,
                    isGateMode = isGateMode,
                    isRetryOfLockedQuiz = isRetryOfLockedQuiz,
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
            withContext(Dispatchers.Main) {
                questions = result.questions
                poolDebug = result.poolDebug
                pickerDebugPath = result.pickerDebugPath
                debugWrongUsed = result.debugWrongUsed

                b.subjectChip.text = "Ders"
                b.nextBtn.isEnabled = true
                b.nextBtn.setOnClickListener { goNext() }

                // Update DEBUG overlay (grade-based picker) including buildMs, dbQueryMs, capReached.
                if (BuildConfig.DEBUG && (pickerDebugPath == "GRADE" || pickerDebugPath == "WRONG_ONLY")) {
                    val sc = repo.lastSubjectCounts.entries.joinToString(",") { "${it.key}=${it.value}" }
                    b.debugPickerText.text = (
                        "DEBUG_PICKER_OK mode=$pickerDebugPath " +
                        "buildMs=${repo.lastBuildMs} dbQueryMs=${repo.lastDbQueryMs} capReached=${if (repo.lastCapReached) 1 else 0} " +
                        "skippedId=${repo.lastSkippedIdCount} skippedRecent=${repo.lastSkippedRecentCount} " +
                        "skippedStemHash=${repo.lastSkippedStemHashCount} skippedSimilar=${repo.lastSkippedSimilarCount} " +
                        "relaxedRecent=${repo.lastRecentRelaxedCount} relaxedSimilar=${repo.lastSimilarRelaxedCount} " +
                        "subjectCounts=[$sc]"
                    )
                }

                b.submitBtn.visibility = View.GONE
                isLgsModeForDebug = isLgsMode
                applyQuizResultAndRender(result, effectiveGrade, isLgsMode)
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
        protectionPrefs: ProtectionPrefs,
        isReplayFromLastTest: Boolean,
        replayQuestionIds: ArrayList<String>?,
        bossLevel: Int,
        isGateMode: Boolean,
        isRetryOfLockedQuiz: Boolean,
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
        val scheduler = WrongQuestionScheduler(context)
        val poolDebug = if (!isLgsMode && effectiveGrade in 1..7) {
            try {
                repo.buildPoolDebugStatsForGrade(effectiveGrade, quizPrefs.difficulty())
            } catch (_: Exception) {
                null
            }
        } else null

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
            isGateMode && isRetryOfLockedQuiz -> {
                pickerPath = if (isLgsMode) "GATE_RETRY_LGS" else "GATE_RETRY"
                val ids = protectionPrefs.lastFailedQuestionIds()
                if (ids.size >= targetCount) {
                    val all = repo.loadAllQuestions().associateBy { it.id }
                    ids.mapNotNull { all[it] }
                } else {
                    when {
                        isLgsMode -> repo.pickQuizQuestionsForLGS(targetCount, quizId)
                        effectiveGrade in 1..7 -> repo.pickGateQuestionsByGrade(effectiveGrade, targetCount)
                        else -> repo.pickGateQuestions(levelGroup, targetCount)
                    }
                }
            }
            isGateMode -> {
                pickerPath = when {
                    isLgsMode -> "GATE_LGS"
                    effectiveGrade in 1..7 -> "GATE_GRADE"
                    else -> "GATE"
                }
                when {
                    isLgsMode -> repo.pickQuizQuestionsForLGS(targetCount, quizId)
                    effectiveGrade in 1..7 -> repo.pickGateQuestionsByGrade(effectiveGrade, targetCount)
                    else -> repo.pickGateQuestions(levelGroup, targetCount)
                }
            }
            isRemedial -> when {
                isLgsMode -> {
                    pickerPath = "REMEDIAL_LGS"
                    repo.pickQuizQuestionsForLGS(targetCount, quizId)
                }
                effectiveGrade in 1..7 -> {
                    pickerPath = "REMEDIAL_GRADE"
                    val (list, usedFallback) = repo.pickRemedialQuestionsByGrade(effectiveGrade, targetCount, protectionPrefs.lastFailedWrongIds())
                    remedialWarning = usedFallback
                    list
                }
                else -> {
                    pickerPath = "REMEDIAL"
                    val (list, usedFallback) = repo.pickRemedialQuestions(levelGroup, targetCount, protectionPrefs.lastFailedWrongIds())
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
                        val categories = subjectFilter?.let { tr ->
                            Subject.entries.find { it.tr == tr }?.let { setOf(it.name) }
                        } ?: quizPrefs.selectedCategories()
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
        if (BuildConfig.DEBUG) {
            android.util.Log.d(
                "TestBuilder",
                "quizId=$quizId pickerPath=$pickerPath total=${q.size} injectedDueId=$injectedDueId"
            )
        }
        return QuizBuildResult(q, poolDebug, pickerPath, remedialWarning, wrongUsed)
    }

    /** Fast fallback when build times out (>5s). Uses simpler pool+shuffle logic. */
    private fun buildQuizRelaxed(
        repo: QuestionRepository,
        quizPrefs: QuizPrefs,
        protectionPrefs: ProtectionPrefs,
        isReplayFromLastTest: Boolean,
        replayQuestionIds: ArrayList<String>?,
        bossLevel: Int,
        isGateMode: Boolean,
        isRetryOfLockedQuiz: Boolean,
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
        val q = when {
            isReplayFromLastTest && replayQuestionIds != null && replayQuestionIds.size >= targetCount -> {
                val all = repo.loadAllQuestions().associateBy { it.id }
                replayQuestionIds.mapNotNull { all[it] }
            }
            isLgsMode -> repo.pickQuizQuestionsForLGS(targetCount, quizId)
            effectiveGrade in 1..7 -> repo.pickQuizQuestionsRelaxedByGrade(effectiveGrade, targetCount, quizId)
            else -> repo.pickQuizQuestionsRelaxed(levelGroup, targetCount, quizPrefs.difficulty(), quizPrefs.selectedCategories(), quizId)
        }
        return QuizBuildResult(q, null, "RELAXED", false, 0)
    }

    private fun applyQuizResultAndRender(result: QuizBuildResult, effectiveGrade: Int, isLgsMode: Boolean = false) {
        if (questions.isEmpty() || questions.size < QuestionRepository.MIN_QUESTIONS_PER_TEST) {
            b.subjectChip.text = "Soru havuzu yetersiz"
            val msg = if (questions.isEmpty()) {
                if (retryWrongMode) "Yanlış cevaplanan soru yok. Önce bir test çöz!"
                else "Soru havuzu yetersiz (en az ${QuestionRepository.MIN_QUESTIONS_PER_TEST} soru gerekli). Veli: Soru paketi ekleyin veya içe aktarın."
            } else "Soru havuzu yetersiz (${questions.size} soru mevcut, en az ${QuestionRepository.MIN_QUESTIONS_PER_TEST} gerekli)."
            val debugSuffix = poolDebug?.readableText?.let { "\n\n$it" } ?: ""
            b.questionText.text = msg + debugSuffix
            b.optionsGroup.visibility = View.GONE
            b.feedbackText.visibility = View.GONE
            b.optA.visibility = View.GONE
            b.optB.visibility = View.GONE
            b.optC.visibility = View.GONE
            b.optD.visibility = View.GONE
            b.btnForceActivateAll.visibility = View.VISIBLE
            b.btnClampDifficulty.visibility = View.VISIBLE
            b.btnFixInvalidGrades.visibility = View.VISIBLE
            val warnings = mutableListOf<String>()
            if (poolDebug?.hasPassiveOnly == true) {
                warnings.add("Sebep: sorular pasif. Çözüm: Tüm Soruları Aktif Yap’a basın.")
            }
            if (poolDebug?.subjectsWithDifficultyGap.orEmpty().isNotEmpty()) {
                warnings.add("Sebep: difficulty mapping hatası. Çözüm: Difficulty 3->2 Düzelt’e basın.")
            }
            if (warnings.isNotEmpty()) {
                b.feedbackText.visibility = View.VISIBLE
                b.feedbackText.setTextColor(android.graphics.Color.RED)
                b.feedbackText.text = warnings.joinToString("\n")
            }
            b.btnForceActivateAll.setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            com.brainbuddy.app.db.DatabaseProvider.get(this@QuizActivity).questionDao().forceActivateAll()
                        } catch (_: Exception) {}
                    }
                    initQuiz(null)
                }
            }
            b.btnClampDifficulty.setOnClickListener {
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            com.brainbuddy.app.db.DatabaseProvider.get(this@QuizActivity).questionDao().clampDifficulty()
                        } catch (_: Exception) {}
                    }
                    val refreshed = try {
                        if (effectiveGrade in 1..7) repo.buildPoolDebugStatsForGrade(effectiveGrade, quizPrefs.difficulty()) else null
                    } catch (_: Exception) { null }
                    b.questionText.text = msg + (refreshed?.readableText?.let { "\n\n$it" } ?: "")
                    val wr = mutableListOf<String>()
                    if (refreshed?.hasPassiveOnly == true) wr.add("Sebep: sorular pasif. Çözüm: Tüm Soruları Aktif Yap’a basın.")
                    if (refreshed?.subjectsWithDifficultyGap.orEmpty().isNotEmpty()) wr.add("Sebep: difficulty mapping hatası. Çözüm: Difficulty 3->2 Düzelt’e basın.")
                    if (wr.isNotEmpty()) {
                        b.feedbackText.visibility = View.VISIBLE
                        b.feedbackText.setTextColor(android.graphics.Color.RED)
                        b.feedbackText.text = wr.joinToString("\n")
                    } else b.feedbackText.visibility = View.GONE
                }
            }
            b.btnFixInvalidGrades.setOnClickListener {
                if (effectiveGrade !in 1..7) return@setOnClickListener
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            com.brainbuddy.app.db.DatabaseProvider.get(this@QuizActivity).questionDao().fixInvalidGrades(effectiveGrade)
                        } catch (_: Exception) {}
                    }
                    initQuiz(null)
                }
            }
            b.nextBtn.isEnabled = true
            b.nextBtn.text = "Ana Sayfaya Dön"
            b.nextBtn.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        } else {
            if (effectiveGrade in 1..7 && questions.size >= 3) {
                questions.take(3).forEachIndexed { i, q ->
                    android.util.Log.d("QuizActivity", "[GRADE_DEBUG] Q${i + 1} id=${q.id} grade=${q.grade} expected=$effectiveGrade")
                    if (q.grade != effectiveGrade) {
                        android.util.Log.w("QuizActivity", "[GRADE_DEBUG] MISMATCH: Q${i + 1} grade=${q.grade} != selectedGrade=$effectiveGrade - exception")
                    }
                }
            }
            if (result.remedialFallbackWarning) {
                android.widget.Toast.makeText(this, "Soru havuzu sınırlı. Veli: Daha fazla soru paketi ekleyin.", android.widget.Toast.LENGTH_LONG).show()
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

    private fun render() {
        if (questions.isEmpty()) return
        index = index.coerceIn(0, questions.size - 1)
        val q = questions[index]

        // Debug header: only show in debug builds and when we have pool diagnostics.
        if (BuildConfig.DEBUG) {
            val effectiveGrade = q.grade
            val selectedDifficulty = quizPrefs.difficulty()
            val debug = poolDebug

            val pickerLabel = pickerDebugPath.ifBlank { if (isLgsModeForDebug) "LGS" else "GRADE" }
            val sc = repo.lastSubjectCounts.entries.joinToString(",") { "${it.key}=${it.value}" }
            val traceLine = "picker=$pickerLabel | buildMs=${repo.lastBuildMs} dbQueryMs=${repo.lastDbQueryMs} capReached=${if (repo.lastCapReached) 1 else 0} subjectCounts=[$sc] | skippedId=${repo.lastSkippedIdCount} skippedStemHash=${repo.lastSkippedStemHashCount} skippedSimilar=${repo.lastSkippedSimilarCount} skippedRecent=${repo.lastSkippedRecentCount} relaxed=${repo.lastRecentRelaxedCount}"

            val modePrefix = if (isLgsModeForDebug) "mode=LGS" else "mode=GRADE" + (if (effectiveGrade in 1..7) " grade=$effectiveGrade" else "")
            if (isLgsModeForDebug) {
                val blueprintLine = repo.lastBlueprintSummary.ifBlank { "LGS_MINI total=20" }
                val typeCountsLine = repo.lastTypeCounts.entries.sortedByDescending { it.value }.joinToString(",") { "${it.key}=${it.value}" }.ifEmpty { "-" }
                val avgQualityLine = String.format("%.1f", repo.lastAvgQualityScore)
                val recentLine = if (repo.lastRecentRelaxedCount > 0) "\nrecentRelaxed=${repo.lastRecentRelaxedCount}" else ""
                val lgsDebug = "$modePrefix | $blueprintLine | typeCounts=[$typeCountsLine] avgQuality=$avgQualityLine capReached=${if (repo.lastCapReached) 1 else 0}$recentLine"
                b.debugInfoText.visibility = View.VISIBLE
                b.debugInfoText.text = "$traceLine\n$lgsDebug"
            } else if (debug != null && effectiveGrade in 1..7) {
                val subjectsOrder = listOf("mat" to "MAT", "turkce" to "TURKCE", "fen" to "FEN", "sosyal" to "SOSYAL", "ing" to "ING")
                val countsLine = subjectsOrder.joinToString("  ") { (key, label) ->
                    val c = debug.perSubject[key]
                    val available = c?.activeDiff ?: 0
                    "$label=$available"
                }
                // Selected type counts per subject (for diversity verification)
                val bySubject = questions.groupBy { it.subject }
                val typeSummary = listOf(
                    Subject.MAT,
                    Subject.TURKCE,
                    Subject.FEN,
                    Subject.SOSYAL,
                    Subject.ING
                ).joinToString(" | ") { subj ->
                    val label = subj.name
                    val typeCounts = bySubject[subj]
                        .orEmpty()
                        .groupBy { it.type }
                        .mapValues { it.value.size }
                        .entries
                        .sortedByDescending { it.value }
                        .joinToString(",") { "${it.key}=${it.value}" }
                        .ifEmpty { "-" }
                    "$label:$typeCounts"
                }
                val wrongUsedText = "$debugWrongUsed/${QuestionRepository.MIN_QUESTIONS_PER_TEST}"
                val headerBase = "$modePrefix • diff=${selectedDifficulty.name} • $countsLine • wrongUsed=$wrongUsedText"
                val recentLine = if (repo.lastRecentRelaxedCount > 0) "\nrecent relaxed +${repo.lastRecentRelaxedCount}" else ""
                val headerRest = "$headerBase$recentLine\n$typeSummary"
                val fullHeader = traceLine + "\n" + headerRest
                b.debugInfoText.visibility = View.VISIBLE
                b.debugInfoText.text = fullHeader
            } else {
                b.debugInfoText.visibility = View.VISIBLE
                b.debugInfoText.text = "$modePrefix • $traceLine"
            }
        } else {
            b.debugInfoText.visibility = View.GONE
        }

        b.progressText.text = "${index + 1}/${questions.size}"
        b.subjectChip.text = "${q.subject.tr} • ${q.gradeTag}"
        b.questionText.text = q.stem

        if (!q.imageAsset.isNullOrBlank()) {
            val path = q.imageAsset!!.trim()
            try {
                assets.open(path).use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (BuildConfig.DEBUG) {
                        val w = bitmap?.width ?: 0
                        val h = bitmap?.height ?: 0
                        android.util.Log.d("QuizActivity", "[VISUAL] imageAsset=$path decodeOk=${bitmap != null} dims=${w}x$h visible=${if (bitmap != null) "VISIBLE" else "GONE"}")
                    }
                    if (bitmap != null) {
                        b.questionImage.setImageBitmap(bitmap)
                        b.questionImage.visibility = View.VISIBLE
                    } else {
                        android.util.Log.w("QuizActivity", "[VISUAL] imageAsset=$path decodeStream returned null")
                        b.questionImage.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("QuizActivity", "[VISUAL] imageAsset=$path open failed", e)
                }
                b.questionImage.visibility = View.GONE
            }
        } else {
            b.questionImage.visibility = View.GONE
        }

        b.optA.text = q.choices.getOrNull(0) ?: "-"
        b.optB.text = q.choices.getOrNull(1) ?: "-"
        b.optC.text = q.choices.getOrNull(2) ?: "-"
        b.optD.text = q.choices.getOrNull(3) ?: "-"

        b.optionsGroup.setOnCheckedChangeListener(null)
        val saved = answers[q.id] ?: -1
        when (saved) {
            0 -> b.optA.isChecked = true
            1 -> b.optB.isChecked = true
            2 -> b.optC.isChecked = true
            3 -> b.optD.isChecked = true
            else -> b.optionsGroup.clearCheck()
        }
        b.optionsGroup.setOnCheckedChangeListener { _, checkedId ->
            val sel = when (checkedId) {
                b.optA.id -> 0
                b.optB.id -> 1
                b.optC.id -> 2
                b.optD.id -> 3
                else -> -1
            }
            if (sel >= 0) {
                answers[q.id] = sel
                if (index == questions.size - 1) {
                    // Auto-submit on last question - no Finish Test button
                    b.nextBtn.postDelayed({ if (!isFinishing) finishTest() }, 600)
                }
            }
        }

        b.feedbackText.visibility = View.GONE
        listOf(b.optA, b.optB, b.optC, b.optD).forEach {
            it.alpha = 1f
            it.setBackgroundColor(android.graphics.Color.TRANSPARENT)
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
        val sel = when (b.optionsGroup.checkedRadioButtonId) {
            b.optA.id -> 0
            b.optB.id -> 1
            b.optC.id -> 2
            b.optD.id -> 3
            else -> -1
        }
        if (sel >= 0) answers[q.id] = sel
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
        val bySubject = questions.groupBy { it.subject.tr }.mapValues { (_, qs) -> qs.size }
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
                val mission = com.brainbuddy.app.core.DailyMissionManager(this@QuizActivity.applicationContext)
                mission.onTestCompleted()
                if (wrongIds.isNotEmpty()) {
                    val correctedWrongCount = answerRecords.count { it.isCorrect && it.questionId in wrongIds }
                    repeat(correctedWrongCount.coerceAtLeast(0)) {
                        mission.onRetrySolved()
                    }
                }
            }
            val isGateMode = intent.getBooleanExtra(EXTRA_GATE_MODE, false)
            val isRemedial = intent.getBooleanExtra(EXTRA_REMEDIAL, false)
            val total = questions.size
            val accuracy = if (total > 0) correctCount.toFloat() / total else 0f
            val answeredCount = correctCount + wrongCount
            val successRate = if (answeredCount > 0) correctCount.toFloat() / answeredCount else 0f
            val protectionPrefsForThreshold = ProtectionPrefs(this@QuizActivity)
            val minSuccessThreshold = protectionPrefsForThreshold.minSuccessRatePercent() / 100f
            val passed = when {
                isGateMode || isRetryOfLockedQuiz || isRemedial ->
                    wrongCount < 4 && successRate >= minSuccessThreshold
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
            val protectionPrefs = ProtectionPrefs(this@QuizActivity)
            val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.trim().orEmpty()
            if (passed && (isGateMode || isRetryOfLockedQuiz || isRemedial)) {
                com.brainbuddy.app.gate.GateManager.onGatePassed(this@QuizActivity, blockedPkg)
                com.brainbuddy.app.core.QuizRetryPolicy(this@QuizActivity).onPass()
            }
            val passedBossLevel = intent.getIntExtra(EXTRA_BOSS_LEVEL, -1)
            if (passed && passedBossLevel > 0) {
                BossTestStore(this@QuizActivity).markBossPassed(passedBossLevel)
            }
            if (!passed && (isGateMode || isRetryOfLockedQuiz) && !isRemedial) {
                com.brainbuddy.app.core.ReportStore(this@QuizActivity).recordLockEvent()
                com.brainbuddy.app.gate.GateManager.onGateFailed(this@QuizActivity, blockedPkg)
                protectionPrefs.setLastFailedWrongIds(wrongIds)
                protectionPrefs.setLastFailedQuizId(quizId)
                protectionPrefs.setLastFailedQuestionIds(questions.map { it.id })
                protectionPrefs.setLastFailedSessionJson(QuizResultActivity.encodeSession(session))
                protectionPrefs.setLastFailedQuestionsJson(QuizResultActivity.encodeQuestions(questions))
                com.brainbuddy.app.core.QuizRetryPolicy(this@QuizActivity).onFail(this@QuizActivity, com.brainbuddy.app.core.QuizRetryPolicy.SameTestToken(quizId, questions.map { it.id }))
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
