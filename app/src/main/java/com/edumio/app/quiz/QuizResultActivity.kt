package com.edumio.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.edumio.app.HomeActivity
import com.edumio.app.R
import com.edumio.app.core.AnalyticsStore
import com.edumio.app.core.StatsRepository
import com.edumio.app.core.GamificationStore
import com.edumio.app.core.PremiumStore
import com.edumio.app.core.QuizPrefs
import com.edumio.app.core.QuizRetryPolicy
import com.edumio.app.core.RetryUnlockStore
import com.edumio.app.core.TestPerformance
import com.edumio.app.core.WrongReviewAccessManager
import com.edumio.app.core.TopicCounts
import com.edumio.app.league.LeagueScoring
import com.edumio.app.league.LeagueStore
import com.edumio.app.quiz.ExamType
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class QuizResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_QUESTIONS_JSON = "extra_questions_json"
        const val EXTRA_IS_RETRY = "extra_is_retry"
        const val EXTRA_IS_GATE_MODE = "extra_is_gate_mode"
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"

        @Deprecated("Use EXTRA_SESSION")
        const val EXTRA_ANSWERS_JSON = "extra_answers_json"
        const val EXTRA_LEVEL = "extra_level"
        const val EXTRA_RETRY_WRONG = "extra_retry_wrong"

        fun encodeSession(s: com.edumio.app.quiz.QuizSession): String {
            val o = JSONObject()
            o.put("quizId", s.quizId)
            o.put("startedAt", s.startedAt)
            o.put("completedAt", s.completedAt ?: JSONObject.NULL)
            o.put("correctCount", s.correctCount)
            o.put("wrongCount", s.wrongCount)
            o.put("blankCount", s.blankCount)
            o.put("passed", s.passed)
            o.put("wrongQuestionIds", JSONArray(s.wrongQuestionIds))
            o.put("reviewCorrectedCount", s.reviewCorrectedCount)
            val answersObj = JSONObject()
            s.answers.forEach { (k, v) -> answersObj.put(k, v) }
            o.put("answers", answersObj)
            o.put("questionIds", JSONArray(s.questionIds))
            return o.toString()
        }

        fun decodeSession(json: String?): com.edumio.app.quiz.QuizSession? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                val wrongArr = o.optJSONArray("wrongQuestionIds") ?: JSONArray()
                val wrongIds = (0 until wrongArr.length()).map { wrongArr.getString(it) }
                val answersObj = o.optJSONObject("answers") ?: JSONObject()
                val answers = answersObj.keys().asSequence().associateWith { answersObj.getInt(it) }
                val qidsArr = o.optJSONArray("questionIds") ?: JSONArray()
                val questionIds = (0 until qidsArr.length()).map { qidsArr.getString(it) }
                com.edumio.app.quiz.QuizSession(
                    quizId = o.optString("quizId", ""),
                    startedAt = o.optLong("startedAt", 0L),
                    questionIds = questionIds,
                    answers = answers,
                    completedAt = if (o.isNull("completedAt")) null else o.getLong("completedAt"),
                    correctCount = o.optInt("correctCount", 0),
                    wrongCount = o.optInt("wrongCount", 0),
                    blankCount = o.optInt("blankCount", 0),
                    passed = o.optBoolean("passed", true),
                    wrongQuestionIds = wrongIds,
                    reviewCorrectedCount = o.optInt("reviewCorrectedCount", 0)
                )
            } catch (_: Exception) { null }
        }

        fun encodeQuestions(list: List<Question>): String {
            val arr = JSONArray()
            list.forEach { q ->
                val o = JSONObject()
                o.put("id", q.id)
                o.put("levelGroup", q.levelGroup.name)
                o.put("subject", q.subject.name)
                o.put("gradeTag", q.gradeTag)
                o.put("grade", q.grade)
                o.put("stem", q.stem)
                o.put("choices", JSONArray(q.choices))
                o.put("correctIndex", q.correctIndex)
                o.put("hint", q.hint ?: JSONObject.NULL)
                o.put("imageAsset", q.imageAsset ?: JSONObject.NULL)
                o.put("difficulty", q.difficulty.name)
                o.put("examType", q.examType.name)
                o.put("type", q.type)
                o.put("skill", q.skill)
                arr.put(o)
            }
            return arr.toString()
        }

        fun decodeQuestions(json: String?): List<Question> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val arr = JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    val levelGroup = try { LevelGroup.valueOf(o.getString("levelGroup")) } catch (_: Exception) { LevelGroup.GRADE_5_8 }
                    val subject = try { Subject.valueOf(o.getString("subject")) } catch (_: Exception) { Subject.MAT }
                    val diff = try { QuizDifficulty.valueOf(o.optString("difficulty", "MEDIUM")) } catch (_: Exception) { QuizDifficulty.MEDIUM }
                    val choicesArr = o.getJSONArray("choices")
                    val choices = (0 until choicesArr.length()).map { choicesArr.getString(it) }
                    val examType = try { ExamType.valueOf(o.optString("examType", "GENERAL")) } catch (_: Exception) { ExamType.GENERAL }
                    val type = o.optString("type", "").takeIf { it.isNotEmpty() } ?: "UNKNOWN"
                    val skill = o.optString("skill", "").takeIf { it.isNotEmpty() } ?: "UNKNOWN"
                    Question(
                        id = o.getString("id"),
                        levelGroup = levelGroup,
                        subject = subject,
                        gradeTag = o.optString("gradeTag", ""),
                        grade = o.optInt("grade", 0).let { g ->
                            if (g in 1..7) g else o.optString("gradeTag", "6").toIntOrNull()?.coerceIn(1, 7) ?: 6
                        },
                        stem = o.optString("stem", "?"),
                        choices = choices,
                        correctIndex = o.optInt("correctIndex", 0),
                        hint = if (o.isNull("hint")) null else o.getString("hint"),
                        imageAsset = if (o.isNull("imageAsset")) null else o.optString("imageAsset", "").takeIf { it.isNotEmpty() },
                        difficulty = diff,
                        examType = examType,
                        topic = o.optString("topic", "").takeIf { it.isNotEmpty() },
                        type = type,
                        skill = skill
                    )
                }
            } catch (_: Exception) { emptyList() }
        }

        fun encodeAnswers(list: List<AnswerRecord>): String {
            val arr = JSONArray()
            list.forEach {
                val o = JSONObject()
                o.put("questionId", it.questionId)
                o.put("selectedIndex", it.selectedIndex)
                o.put("correctIndex", it.correctIndex)
                arr.put(o)
            }
            return arr.toString()
        }

        fun decodeAnswers(json: String?): List<AnswerRecord> {
            if (json.isNullOrBlank()) return emptyList()
            val arr = JSONArray(json)
            val out = ArrayList<AnswerRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(AnswerRecord(o.getString("questionId"), o.getInt("selectedIndex"), o.getInt("correctIndex")))
            }
            return out
        }
    }

    private var session: com.edumio.app.quiz.QuizSession? = null
    private var questions: List<Question> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_result)

        session = decodeSession(intent.getStringExtra(EXTRA_SESSION))
        questions = decodeQuestions(intent.getStringExtra(EXTRA_QUESTIONS_JSON))
        if (questions.isEmpty() && session != null) {
            val repo = QuestionRepository(this)
            val allMap = repo.loadAllQuestions().associateBy { it.id }
            questions = session!!.wrongQuestionIds.mapNotNull { allMap[it] }
        }

        val s = session ?: run {
            findViewById<android.widget.TextView>(R.id.tvTitle).text = getString(R.string.quiz_result_load_error)
            return
        }

        val gam = GamificationStore(this)
        val analytics = AnalyticsStore(this)
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val lastDay = TimeUnit.MILLISECONDS.toDays(gam.lastCompletedMs())
        val isStreakDay = gam.lastCompletedMs() == 0L || lastDay == today || lastDay == today - 1
        val streakBefore = gam.streakDays()
        gam.recordQuizCompletion(System.currentTimeMillis())
        val newStreak = gam.streakDays()
        val newMilestone = when {
            newStreak >= 100 && streakBefore < 100 -> "Legend Streak!"
            newStreak >= 30 && streakBefore < 30 -> "30-Day Avatar Unlock!"
            newStreak >= 7 && streakBefore < 7 -> "7-Day Streak Badge!"
            else -> null
        }
        val xpBefore = gam.xp()
        gam.addXpForQuiz(s.correctCount, s.wrongCount, isStreakDay)
        val xpEarned = gam.xp() - xpBefore

        val perf = buildTestPerformance(s)
        analytics.recordTestPerformance(perf)
        StatsRepository.notifyQuizSavedGlobal()
        analytics.recordSession(com.edumio.app.core.QuizSession(
            System.currentTimeMillis(), s.correctCount, s.totalCount, xpEarned
        ))

        if (s.totalCount >= com.edumio.app.quiz.QuestionRepository.MIN_QUESTIONS_PER_TEST) {
            val leagueStore = LeagueStore(this)
            val testIndexOfDay = leagueStore.getTestsCompletedToday()
            val isGateFailForLeague = intent.getBooleanExtra(EXTRA_IS_GATE_MODE, false) && !s.passed
            val streakDays = com.edumio.app.core.GamificationStore(this).streakDays()
            val breakdown = LeagueScoring.computeBreakdown(
                s.wrongCount, s.blankCount, isGateFailForLeague, testIndexOfDay, streakDays
            )
            leagueStore.addWeeklyScore(breakdown.finalPoints)
            leagueStore.incrementTestsToday()
            leagueStore.recordPointsBreakdown(breakdown, s.quizId, s.completedAt ?: System.currentTimeMillis())
        }

        renderResultProgress()

        val total = s.totalCount
        val pct = if (total > 0) (100f * s.correctCount / total) else 0f
        val accuracy = if (total > 0) s.correctCount.toFloat() / total else 0f
        val isGateMode = intent.getBooleanExtra(EXTRA_IS_GATE_MODE, false)
        val blockedPkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)?.trim().orEmpty()
        val isGateFail = isGateMode && !s.passed

        val titleText = when {
            !s.passed -> getString(R.string.lock_failed_message)
            isGateMode && s.wrongCount <= 3 -> if (accuracy >= 0.4f) getString(R.string.quiz_result_congrats) else getString(R.string.quiz_result_completed)
            s.passed -> if (accuracy >= 0.4f) getString(R.string.quiz_result_congrats) else getString(R.string.quiz_result_completed)
            else -> getString(R.string.quiz_result_completed)
        }
        findViewById<android.widget.TextView>(R.id.tvTitle).text = titleText
        findViewById<android.widget.TextView>(R.id.tvScoreBig).text = "${s.correctCount}/${total}"
        findViewById<android.widget.TextView>(R.id.tvScoreLabel).text =
            "Doğru: ${s.correctCount}/$total | Yanlış: ${s.wrongCount}/$total | Boş: ${s.blankCount}/$total"
        findViewById<android.widget.TextView>(R.id.tvPercentage).apply {
            visibility = View.VISIBLE
            text = "%.0f%%".format(pct)
        }
        findViewById<android.widget.TextView>(R.id.tvPassFail).apply {
            visibility = View.VISIBLE
            if (isGateMode) {
                // Legacy gate mode keeps an explicit pass/fail.
                text = if (s.passed) "✅ GEÇTİ" else "❌ BAŞARISIZ"
                setTextColor(if (s.passed) getColor(R.color.bb_turquoise) else getColor(R.color.bb_error))
                if (!s.passed) textSize = 24f
            } else {
                // Practice is never a "failure" — it's a completed step. No anxiety.
                text = getString(R.string.quiz_result_step_done)
                setTextColor(getColor(R.color.bb_turquoise))
            }
        }

        findViewById<android.widget.ProgressBar>(R.id.progressCircle).apply {
            max = 100
            progress = pct.toInt()
        }

        findViewById<android.widget.TextView>(R.id.tvPoints).text = "+${xpEarned} XP"
        findViewById<android.widget.TextView>(R.id.tvStreak).text = "🔥 ${gam.streakDays()} gün seri"
        findViewById<android.widget.TextView>(R.id.tvLevel).apply {
            visibility = View.VISIBLE
            val (into, of) = gam.xpProgress()
            text = "Seviye ${gam.level()} ($into/$of XP)"
        }

        val (strongest, weakest, suggested) = computeAnalyticsSummaryWithCounts(perf)
        findViewById<android.widget.TextView>(R.id.tvAnalytics).apply {
            visibility = View.VISIBLE
            // Specific learning feedback, not generic praise.
            val parts = ArrayList<String>()
            if (s.reviewCorrectedCount > 0) {
                parts.add(getString(R.string.result_review_corrected, s.reviewCorrectedCount))
            }
            if (suggested.isNotBlank() && suggested != "-") {
                parts.add(getString(R.string.result_next_focus, suggested))
            }
            text = if (parts.isEmpty()) getString(R.string.student_encouragement) else parts.joinToString("\n")
        }

        // Wrong answers inline list hidden — review via dedicated gated screen
        val wrongSection = findViewById<View>(R.id.wrongSection)
        wrongSection.visibility = View.GONE

        val wrongIds = s.wrongQuestionIds
        val reviewCard = findViewById<View>(R.id.reviewConversionCard)
        val btnRetryWrong = findViewById<android.widget.Button>(R.id.btnRetryWrong)
        val tvReviewInfo = findViewById<android.widget.TextView>(R.id.tvReviewInfo)
        val tvLockIcon = findViewById<android.widget.TextView>(R.id.tvReviewLockIcon)
        val tvWrongCount = findViewById<android.widget.TextView>(R.id.tvReviewWrongCount)
        val tvQuotaDots = findViewById<android.widget.TextView>(R.id.tvResultQuotaDots)
        val tvPremiumHint = findViewById<android.widget.TextView>(R.id.tvResultPremiumHint)

        if (wrongIds.isEmpty()) {
            reviewCard.visibility = View.GONE
        } else {
            reviewCard.visibility = View.VISIBLE
            val am = WrongReviewAccessManager(this)
            am.handleDailyReset()
            val isPrem = am.isPremium()

            // Lock icon + wrong count
            tvLockIcon.text = if (isPrem) "\u2705" else "\uD83D\uDD12"
            tvWrongCount.text = getString(R.string.result_wrong_count, wrongIds.size)

            // Quota dots + info
            if (isPrem) {
                tvQuotaDots.visibility = View.GONE
                tvReviewInfo.text = getString(R.string.result_premium_all_unlocked)
                tvPremiumHint.visibility = View.GONE
            } else {
                val used = am.getUsedToday()
                val max = WrongReviewAccessManager.FREE_PER_DAY
                val remaining = max - used
                tvQuotaDots.visibility = View.VISIBLE
                val dots = StringBuilder()
                for (i in 0 until max) {
                    if (i > 0) dots.append("  ")
                    dots.append(if (i < used) "\u25CF" else "\u25CB")
                }
                tvQuotaDots.text = dots
                tvReviewInfo.text = if (remaining > 0) {
                    getString(R.string.result_review_info_free, remaining, max)
                } else {
                    getString(R.string.result_review_limit_done)
                }
                tvPremiumHint.visibility = View.VISIBLE
                tvPremiumHint.text = getString(R.string.wrong_review_premium_upsell)
            }

            // Button text
            if (isGateMode && !s.passed) {
                btnRetryWrong.setText(R.string.wrong_review_btn_show_detail)
            }

            btnRetryWrong.setOnClickListener {
                startActivity(Intent(this, WrongAnswerReviewActivity::class.java).apply {
                    putStringArrayListExtra(WrongAnswerReviewActivity.EXTRA_WRONG_IDS, ArrayList(wrongIds))
                    putExtra(WrongAnswerReviewActivity.EXTRA_SESSION_JSON, encodeSession(s))
                    if (isGateMode && !s.passed) {
                        putExtra(WrongAnswerReviewActivity.EXTRA_GATE_FAIL_REVIEW, true)
                    }
                })
            }
        }

        findViewById<android.widget.Button>(R.id.btnRetryTest).setOnClickListener {
            val minQuestions = QuizPrefs(this).questionsPerSession()
            if (isGateFail) {
                val policy = QuizRetryPolicy(this)
                val token = policy.getSameTestToken()
                val qId = token?.quizId ?: ""
                val qIds = token?.questionIds ?: emptyList()
                when (policy.getStartMode()) {
                    QuizRetryPolicy.StartMode.WAIT_COOLDOWN -> {
                        // Cooldown — let the user wait; no routing to a deleted screen
                    }
                    else -> {
                        val retryIntent = Intent(this, QuizActivity::class.java).apply {
                            putExtra(QuizActivity.EXTRA_IS_RETRY, true)
                            putExtra(QuizActivity.EXTRA_QUIZ_ID, qId)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        if (!PremiumStore(this).isPremium()) {
                            val retryToken = RetryUnlockStore(this).createRetryToken(qId, qIds)
                            retryIntent.putExtra(QuizActivity.EXTRA_RETRY_UNLOCK_TOKEN, retryToken)
                        }
                        startActivity(retryIntent)
                    }
                }
            } else {
                startActivity(Intent(this, QuizActivity::class.java))
            }
            finish()
        }

        findViewById<android.widget.Button>(R.id.btnPlayAgain).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
            finish()
        }

        if (s.passed) {
            newMilestone?.let { msg ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.quiz_result_celebration_title))
                    .setMessage(msg)
                    .setPositiveButton(getString(R.string.quiz_result_great), null)
                    .show()
            }
        }

        fun goHome() {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
        findViewById<android.widget.Button>(R.id.btnHome).setOnClickListener { goHome() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })

        val showRetry = isGateFail
        findViewById<android.widget.Button>(R.id.btnRetryTest).apply {
            visibility = if (showRetry) View.VISIBLE else View.GONE
        }
        findViewById<android.widget.Button>(R.id.btnPlayAgain).apply {
            visibility = if (showRetry) View.GONE else View.VISIBLE
        }

        val remedialSection = findViewById<View>(R.id.remedialRetrySection)
        val btnRemedial = findViewById<android.widget.Button>(R.id.btnRemedialMiniTest)
        if (showRetry && wrongIds.isNotEmpty()) {
            remedialSection.visibility = View.VISIBLE
            btnRemedial.setOnClickListener {
                startActivity(Intent(this, com.edumio.app.quiz.QuizActivity::class.java).apply {
                    putExtra(com.edumio.app.quiz.QuizActivity.EXTRA_REMEDIAL, true)
                })
                finish()
            }
        } else {
            remedialSection.visibility = View.GONE
        }
    }

    /**
     * Shows a small, measured "one step closer" line, plus any milestone that
     * the just-recorded result actually unlocked (real-data, celebrated once).
     */
    private fun renderResultProgress() {
        val card = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardResultProgress)
        val tv = findViewById<android.widget.TextView>(R.id.tvResultProgress)
        val newly = com.edumio.app.core.AchievementEngine(this).consumeNewlyUnlocked()
        val lines = ArrayList<String>()
        if (newly.isNotEmpty()) {
            lines.add(getString(R.string.result_achievement_unlocked))
            lines.add(newly.joinToString("   ") { "${it.icon} ${it.title}" })
        }

        // Frame the result as a completed step toward Italy — real data, calm.
        val mission = com.edumio.app.core.DailyMissionManager(this).getTodayMission()
        if (mission.testsDone >= mission.testsTarget) {
            lines.add(getString(R.string.result_mission_done))
        } else {
            lines.add(getString(R.string.result_one_step_closer))
        }
        val pending = com.edumio.app.quiz.WrongQuestionScheduler(this).reviewQueueSize()
        if (pending in 1..99) {
            lines.add(getString(R.string.result_review_queue, pending))
        }
        com.edumio.app.core.ProgressAffirmations(this).headline()?.let { lines.add("✨ $it") }

        tv.text = lines.joinToString("\n")

        // Gentle reveal — earned, not flashy.
        card.visibility = View.VISIBLE
        card.alpha = 0f
        card.translationY = 24f
        card.animate().alpha(1f).translationY(0f).setStartDelay(220).setDuration(320).start()
        if (newly.isNotEmpty()) {
            card.postDelayed({ com.edumio.app.ui.Interactions.success(card) }, 260)
        }
    }

    private fun buildTestPerformance(s: com.edumio.app.quiz.QuizSession): TestPerformance {
        val byTopicCounts = mutableMapOf<String, MutableList<Triple<Int, Int, Int>>>()
        val byDiffCounts = mutableMapOf<String, MutableList<Triple<Int, Int, Int>>>()
        for (q in questions) {
            val sel = s.answers[q.id] ?: -1
            val (c, w, b) = when {
                sel == q.correctIndex -> Triple(1, 0, 0)
                sel == -1 -> Triple(0, 0, 1)
                else -> Triple(0, 1, 0)
            }
            val topic = q.subject.tr
            byTopicCounts.getOrPut(topic) { mutableListOf() }.add(Triple(c, w, b))
            val diff = q.difficulty.name.lowercase()
            byDiffCounts.getOrPut(diff) { mutableListOf() }.add(Triple(c, w, b))
        }
        val topicCounts = byTopicCounts.mapValues { (_, list) ->
            TopicCounts(
                correct = list.sumOf { it.first },
                wrong = list.sumOf { it.second },
                blank = list.sumOf { it.third },
                total = list.size
            )
        }
        val diffCounts = byDiffCounts.mapValues { (_, list) ->
            TopicCounts(
                correct = list.sumOf { it.first },
                wrong = list.sumOf { it.second },
                blank = list.sumOf { it.third },
                total = list.size
            )
        }
        val topicAcc = topicCounts.mapValues { it.value.accuracy }
        val diffAcc = diffCounts.mapValues { it.value.accuracy }
        return TestPerformance(
            quizId = s.quizId,
            tsMs = s.completedAt ?: System.currentTimeMillis(),
            accuracy = s.accuracy,
            correctCount = s.correctCount,
            wrongCount = s.wrongCount,
            blankCount = s.blankCount,
            totalQuestions = s.totalCount,
            passed = s.passed,
            wrongQuestionIds = s.wrongQuestionIds,
            questionIds = s.questionIds,
            byTopic = topicAcc,
            byDifficulty = diffAcc,
            byTopicCounts = topicCounts,
            byDifficultyCounts = diffCounts
        )
    }

    private fun computeAnalyticsSummaryWithCounts(perf: TestPerformance): Triple<String, String, String> {
        val counts = perf.byTopicCounts
        val strongest = if (counts.isEmpty()) {
            perf.byTopic.maxByOrNull { it.value }?.let { "En güçlü konu: ${it.key} (${"%.0f".format(it.value)}%)" } ?: "En güçlü konu: -"
        } else {
            counts.maxByOrNull { it.value.accuracy }?.let { (topic, tc) ->
                "En güçlü konu: $topic ${tc.correct}/${tc.total} doğru (${tc.wrong} yanlış)"
            } ?: "En güçlü konu: -"
        }
        val weakest = if (counts.isEmpty()) {
            perf.byTopic.minByOrNull { it.value }?.let { "Çalışılacak: ${it.key} (${"%.0f".format(it.value)}%)" } ?: "Çalışılacak: -"
        } else {
            counts.minByOrNull { it.value.accuracy }?.let { (topic, tc) ->
                "Çalışılacak: $topic ${tc.correct}/${tc.total} doğru (${tc.wrong} yanlış)"
            } ?: "Çalışılacak: -"
        }
        val suggested = perf.byTopicCounts.minByOrNull { it.value.accuracy }?.key
            ?: perf.byTopic.minByOrNull { it.value }?.key ?: "-"
        return Triple(strongest, weakest, suggested)
    }
}
