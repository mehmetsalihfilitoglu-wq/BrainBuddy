package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.HomeActivity
import com.brainbuddy.app.LockScreenActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AdsPrefs
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.RewardedRetryStore
import com.brainbuddy.app.ads.RewardAdHelper
import com.brainbuddy.app.core.TestPerformance
import com.brainbuddy.app.core.TopicCounts
import com.brainbuddy.app.league.LeagueScoring
import com.brainbuddy.app.league.LeagueStore
import com.brainbuddy.app.quiz.ExamType
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class QuizResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SESSION = "extra_session"
        const val EXTRA_QUESTIONS_JSON = "extra_questions_json"
        const val EXTRA_IS_RETRY = "extra_is_retry"
        const val EXTRA_IS_GATE_MODE = "extra_is_gate_mode"

        @Deprecated("Use EXTRA_SESSION")
        const val EXTRA_ANSWERS_JSON = "extra_answers_json"
        const val EXTRA_LEVEL = "extra_level"
        const val EXTRA_RETRY_WRONG = "extra_retry_wrong"

        fun encodeSession(s: com.brainbuddy.app.quiz.QuizSession): String {
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

        fun decodeSession(json: String?): com.brainbuddy.app.quiz.QuizSession? {
            if (json.isNullOrBlank()) return null
            return try {
                val o = JSONObject(json)
                val wrongArr = o.optJSONArray("wrongQuestionIds") ?: JSONArray()
                val wrongIds = (0 until wrongArr.length()).map { wrongArr.getString(it) }
                val answersObj = o.optJSONObject("answers") ?: JSONObject()
                val answers = answersObj.keys().asSequence().associateWith { answersObj.getInt(it) }
                val qidsArr = o.optJSONArray("questionIds") ?: JSONArray()
                val questionIds = (0 until qidsArr.length()).map { qidsArr.getString(it) }
                com.brainbuddy.app.quiz.QuizSession(
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
                o.put("stem", q.stem)
                o.put("choices", JSONArray(q.choices))
                o.put("correctIndex", q.correctIndex)
                o.put("hint", q.hint ?: JSONObject.NULL)
                o.put("imageAsset", q.imageAsset ?: JSONObject.NULL)
                o.put("difficulty", q.difficulty.name)
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
                    Question(
                        id = o.getString("id"),
                        levelGroup = levelGroup,
                        subject = subject,
                        gradeTag = o.optString("gradeTag", ""),
                        stem = o.optString("stem", "?"),
                        choices = choices,
                        correctIndex = o.optInt("correctIndex", 0),
                        hint = if (o.isNull("hint")) null else o.getString("hint"),
                        imageAsset = if (o.isNull("imageAsset")) null else o.optString("imageAsset", "").takeIf { it.isNotEmpty() },
                        difficulty = diff,
                        examType = try { ExamType.valueOf(o.optString("examType", "GENERAL")) } catch (_: Exception) { ExamType.GENERAL },
                        topic = o.optString("topic", "").takeIf { it.isNotEmpty() }
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

    private var session: com.brainbuddy.app.quiz.QuizSession? = null
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
            findViewById<android.widget.TextView>(R.id.tvTitle).text = "Sonuç yüklenemedi"
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
        analytics.recordSession(com.brainbuddy.app.core.QuizSession(
            System.currentTimeMillis(), s.correctCount, s.totalCount, xpEarned
        ))

        if (s.totalCount == com.brainbuddy.app.quiz.QuestionRepository.MIN_QUESTIONS_PER_TEST) {
            val leagueStore = LeagueStore(this)
            val testIndexOfDay = leagueStore.getTestsCompletedToday()
            val isGateFail = intent.getBooleanExtra(EXTRA_IS_GATE_MODE, false) && s.wrongCount >= 4
            val breakdown = LeagueScoring.computeBreakdown(
                s.wrongCount, s.blankCount, isGateFail, testIndexOfDay
            )
            leagueStore.addWeeklyScore(breakdown.finalPoints)
            leagueStore.incrementTestsToday()
            leagueStore.recordPointsBreakdown(breakdown, s.quizId, s.completedAt ?: System.currentTimeMillis())
        }

        val total = s.totalCount
        val pct = if (total > 0) (100f * s.correctCount / total) else 0f
        val accuracy = if (total > 0) s.correctCount.toFloat() / total else 0f
        val isGateMode = intent.getBooleanExtra(EXTRA_IS_GATE_MODE, false)

        val titleText = when {
            !s.passed -> getString(R.string.lock_failed_message)
            isGateMode && s.wrongCount <= 3 -> if (accuracy >= 0.4f) "Tebrikler! 🎉" else "Tamamlandı"
            s.passed -> if (accuracy >= 0.4f) "Tebrikler! 🎉" else "Tamamlandı"
            else -> "Tamamlandı"
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
            text = if (s.passed) "✅ GEÇTİ" else "❌ BAŞARISIZ"
            setTextColor(if (s.passed) getColor(R.color.bb_turquoise) else getColor(R.color.bb_error))
            if (!s.passed) textSize = 24f
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
            text = if (suggested.isNotBlank() && suggested != "-") {
                getString(R.string.student_encouragement) + "\n$suggested konusunda pratik yap."
            } else {
                getString(R.string.student_encouragement)
            }
        }

        val wrongSection = findViewById<View>(R.id.wrongSection)
        val btnRetryWrong = findViewById<android.widget.Button>(R.id.btnRetryWrong)
        val wrongIds = s.wrongQuestionIds
        val isFailedScreen = isGateMode && s.wrongCount >= 4
        if (wrongIds.isEmpty() || isFailedScreen) {
            wrongSection.visibility = View.GONE
            btnRetryWrong.visibility = View.GONE
        } else {
            wrongSection.visibility = View.VISIBLE
            btnRetryWrong.visibility = View.VISIBLE
            showWrongAnswers(s, wrongIds)
        }

        btnRetryWrong.setOnClickListener {
            startActivity(Intent(this, WrongAnswerReviewActivity::class.java).apply {
                putStringArrayListExtra(WrongAnswerReviewActivity.EXTRA_WRONG_IDS, ArrayList(wrongIds))
                putExtra(WrongAnswerReviewActivity.EXTRA_SESSION_JSON, encodeSession(s))
            })
        }

        findViewById<android.widget.Button>(R.id.btnRetryTest).setOnClickListener {
            val protectionPrefs = ProtectionPrefs(this)
            if (protectionPrefs.userLocked()) {
                startActivity(Intent(this, QuizActivity::class.java).apply {
                    putExtra(QuizActivity.EXTRA_GATE_MODE, true)
                    putExtra(QuizActivity.EXTRA_IS_RETRY, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
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
                android.app.AlertDialog.Builder(this)
                    .setTitle("🎉 Kutlama!")
                    .setMessage(msg)
                    .setPositiveButton("Harika!", null)
                    .show()
            }
        }

        findViewById<android.widget.Button>(R.id.btnHome).setOnClickListener {
            if (ProtectionPrefs(this).userLocked()) {
                startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            } else {
                startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            finish()
        }

        val locked = ProtectionPrefs(this).userLocked()
        findViewById<android.widget.Button>(R.id.btnRetryTest).apply {
            visibility = if (locked) View.VISIBLE else View.GONE
        }
        findViewById<android.widget.Button>(R.id.btnPlayAgain).apply {
            visibility = if (locked) View.GONE else View.VISIBLE
        }

        val adSection = findViewById<View>(R.id.adRetrySection)
        val btnWatchAd = findViewById<android.widget.Button>(R.id.btnWatchAd)
        val tvAdRetryInfo = findViewById<android.widget.TextView>(R.id.tvAdRetryInfo)
        val remedialSection = findViewById<View>(R.id.remedialRetrySection)
        val btnRemedial = findViewById<android.widget.Button>(R.id.btnRemedialMiniTest)
        if (locked && wrongIds.isNotEmpty()) {
            remedialSection.visibility = View.VISIBLE
            btnRemedial.setOnClickListener {
                startActivity(Intent(this, com.brainbuddy.app.quiz.QuizActivity::class.java).apply {
                    putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_REMEDIAL, true)
                })
                finish()
            }
        } else {
            remedialSection.visibility = View.GONE
        }
        if (locked && isGateMode && wrongIds.isNotEmpty()) {
            val adsPrefs = AdsPrefs(this)
            val premiumStore = PremiumStore(this)
            val retryStore = RewardedRetryStore(this)
            val profileId = ProfileStore(this).getCurrentProfileId()
            val eligibleQuestion = wrongIds.shuffled().firstOrNull { qId ->
                retryStore.canRetryWithAd(profileId, s.quizId, qId)
            }
            val isPremium = premiumStore.isPremium()
            val canShowAd = !isPremium && adsPrefs.isAdsEnabled() && eligibleQuestion != null
            val canShowPremiumRetry = isPremium && eligibleQuestion != null
            if (canShowAd || canShowPremiumRetry) {
                adSection.visibility = View.VISIBLE
                if (isPremium) {
                    tvAdRetryInfo.text = "Premium: Bir yanlış soruyu tekrar cevapla (sınırsız)."
                    btnWatchAd.text = "Tekrar Dene"
                    btnWatchAd.setOnClickListener {
                        val q = wrongIds.shuffled().firstOrNull { retryStore.canRetryWithAd(profileId, s.quizId, it) } ?: wrongIds.first()
                        retryStore.recordRetryUsed(profileId, s.quizId, q)
                        startActivity(Intent(this, GateRetrySingleActivity::class.java).apply {
                            putExtra(GateRetrySingleActivity.EXTRA_QUIZ_ID, s.quizId)
                            putExtra(GateRetrySingleActivity.EXTRA_QUESTION_ID, q)
                            putExtra(GateRetrySingleActivity.EXTRA_SESSION_JSON, encodeSession(s))
                            putExtra(GateRetrySingleActivity.EXTRA_QUESTIONS_JSON, intent.getStringExtra(EXTRA_QUESTIONS_JSON))
                        })
                        finish()
                    }
                } else {
                    tvAdRetryInfo.text = "Reklam izleyerek bir yanlış soruyu tekrar cevaplayabilirsin. Bugün kalan: ${retryStore.getRemainingRetriesToday(profileId)}"
                    btnWatchAd.text = "Reklam İzle → Tekrar Dene"
                    val adHelper = RewardAdHelper(this)
                    adHelper.loadAd(onFailed = { btnWatchAd.isEnabled = false })
                    btnWatchAd.setOnClickListener {
                        if (adHelper.isLoaded()) {
                            adHelper.showAd(
                                onRewarded = {
                                    retryStore.recordRetryUsed(profileId, s.quizId, eligibleQuestion!!)
                                    startActivity(Intent(this, GateRetrySingleActivity::class.java).apply {
                                        putExtra(GateRetrySingleActivity.EXTRA_QUIZ_ID, s.quizId)
                                        putExtra(GateRetrySingleActivity.EXTRA_QUESTION_ID, eligibleQuestion)
                                        putExtra(GateRetrySingleActivity.EXTRA_SESSION_JSON, encodeSession(s))
                                        putExtra(GateRetrySingleActivity.EXTRA_QUESTIONS_JSON, intent.getStringExtra(EXTRA_QUESTIONS_JSON))
                                    })
                                    finish()
                                },
                                onFailed = { adHelper.loadAd() }
                            )
                        } else {
                            adHelper.loadAd()
                        }
                    }
                }
            } else {
                adSection.visibility = View.GONE
            }
        } else {
            adSection.visibility = View.GONE
        }
    }

    private fun buildTestPerformance(s: com.brainbuddy.app.quiz.QuizSession): TestPerformance {
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

    private fun showWrongAnswers(s: com.brainbuddy.app.quiz.QuizSession, wrongIds: List<String>) {
        val repo = QuestionRepository(this)
        val allMap = repo.loadAllQuestions().associateBy { it.id }
        val items = wrongIds.mapNotNull { id ->
            val q = allMap[id] ?: return@mapNotNull null
            val sel = s.answers[id] ?: -1
            val userChoice = if (sel in 0..3) q.choices.getOrNull(sel) ?: "?" else "-"
            val correctChoice = q.choices.getOrNull(q.correctIndex) ?: "?"
            WrongItem(q.stem, userChoice, correctChoice, q.hint, showCorrect = false)
        }
        val recycler = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerWrong)
        recycler.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recycler.adapter = WrongAnswersAdapter(items)
    }

    data class WrongItem(val question: String, val userChoice: String, val correctAnswer: String, val hint: String?, val showCorrect: Boolean = false)

    class WrongAnswersAdapter(private val items: List<WrongItem>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<WrongAnswersAdapter.VH>() {
        class VH(val view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_wrong_answer, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.view.findViewById<android.widget.TextView>(R.id.tvQuestion).text = item.question
            holder.view.findViewById<android.widget.TextView>(R.id.tvUserChoice).apply {
                visibility = android.view.View.VISIBLE
                text = "Senin cevabın: ${item.userChoice}\n❌ Yanlış"
                setTextColor(holder.view.context.getColor(R.color.bb_error))
            }
            holder.view.findViewById<android.widget.TextView>(R.id.tvCorrect).apply {
                visibility = if (item.showCorrect) android.view.View.VISIBLE else android.view.View.GONE
                text = "✓ Doğru: ${item.correctAnswer}"
            }
            holder.view.findViewById<android.widget.TextView>(R.id.tvHint).apply {
                visibility = if (item.showCorrect && !item.hint.isNullOrBlank()) android.view.View.VISIBLE else android.view.View.GONE
                text = "💡 ${item.hint}"
            }
        }

        override fun getItemCount() = items.size
    }
}
