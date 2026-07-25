package com.edumio.app.dailychallenge

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.edumio.app.R
import com.edumio.app.core.ExamType
import com.edumio.app.db.DatabaseProvider
import com.edumio.app.quiz.PremiumPaywallSheet
import com.edumio.app.solutions.SolutionAccessPolicy
import com.edumio.app.solutions.SolutionActivity
import com.edumio.app.ui.onTap
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The Premium wrong-question hub: every previously-served wrong question of the account, with the two
 * paths of the learning model — Retry Question (answer again, correct → leaves the active pool via the
 * mastery state machine) and View Solution (read-only; NEVER removes anything). Free users keep their
 * capped review flow; here they see the counts and a locked list with a Premium CTA (no stems bound).
 *
 * Data comes exclusively from already-served state rows — this screen can never surface a new
 * question or touch the Daily Challenge in any way.
 */
class WrongQuestionsActivity : AppCompatActivity() {

    private data class Row(
        val questionId: String,
        val exam: String,
        val section: String,
        val topic: String,
        val state: QuestionLearnState,
        val timesIncorrect: Int,
        val lastSeenAt: Long,
        val nextReviewAt: Long,
        val stem: String,
    )

    private val activeStates = listOf(
        QuestionLearnState.INCORRECT_ONCE.name, QuestionLearnState.INCORRECT_MULTIPLE.name,
        QuestionLearnState.FORGOTTEN.name,
    )
    private val scheduledStates = listOf(QuestionLearnState.NEEDS_REVISION.name)
    private val resolvedStates = listOf(QuestionLearnState.NEEDS_REVISION.name, QuestionLearnState.MASTERED.name)

    private var examFilter: ExamType? = null // null = all
    private var sort = Sort.DUE
    private enum class Sort { DUE, RECENT, MOST_WRONG }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wrong_questions)
        buildChips()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    /**
     * Exam filter + sort selectors. Both render through the shared [com.edumio.app.ui.FilterChips], so the
     * ACTIVE option is always filled/highlighted and the touch targets meet the 48dp guideline — previously
     * every chip painted the same background (the user could not tell what was selected) and its size was
     * set in raw pixels, which made the row tiny and cramped on a high-density screen.
     */
    private fun buildChips() {
        val exams = listOf<Pair<String, ExamType?>>(
            getString(R.string.wp_filter_all) to null,
            ExamType.IMAT.code to ExamType.IMAT,
            ExamType.TIL_I.code to ExamType.TIL_I,
            ExamType.CENT_S.code to ExamType.CENT_S,
        )
        com.edumio.app.ui.FilterChips.bind(
            findViewById(R.id.wpExamFilter), exams, examFilter,
        ) { examFilter = it; refresh() }

        val sorts = listOf(
            getString(R.string.wp_sort_due) to Sort.DUE,
            getString(R.string.wp_sort_recent) to Sort.RECENT,
            getString(R.string.wp_sort_most_wrong) to Sort.MOST_WRONG,
        )
        com.edumio.app.ui.FilterChips.bind(
            findViewById(R.id.wpSort), sorts, sort,
        ) { sort = it; refresh() }
    }

    /**
     * ERROR state — the backing queries failed. Deliberately separate from the EMPTY state: an
     * unreadable database must never be presented as "you have no wrong questions". Retry re-runs
     * the load.
     */
    private fun showLoadError() {
        findViewById<LinearLayout>(R.id.wpList).removeAllViews()
        findViewById<LinearLayout>(R.id.wpEmpty).visibility = View.GONE
        findViewById<LinearLayout>(R.id.wpLocked).visibility = View.GONE
        findViewById<LinearLayout>(R.id.wpError).visibility = View.VISIBLE
        findViewById<Button>(R.id.wpErrorRetry).onTap { refresh() }
    }

    private fun refresh() {
        val userId = DailyChallengeUser.resolve(this)
        val premium = DailyChallengeEntitlement.isPremiumForSolutions(this)
        val access = SolutionAccessPolicy.access(premium)
        val list = findViewById<LinearLayout>(R.id.wpList)
        val empty = findViewById<LinearLayout>(R.id.wpEmpty)
        val locked = findViewById<LinearLayout>(R.id.wpLocked)
        val error = findViewById<LinearLayout>(R.id.wpError)

        lifecycleScope.launch {
            val dao = DailyChallengeDatabase.get(this@WrongQuestionsActivity).dailyChallengeDao()
            val now = System.currentTimeMillis()

            fun List<UserQuestionStateEntity>.filtered() =
                if (examFilter == null) this else filter { it.examType == examFilter!!.name }

            // Each query used to swallow its exception into an empty list, so a DATABASE FAILURE was
            // rendered as the congratulation "Aktif yanlış sorun yok. Böyle devam!" — the app reported
            // success for data it could not read. Load once, and keep failure distinct from empty.
            val loaded = try {
                val a = dao.getStatesByStatesAllExams(userId, activeStates).filtered()
                val s = dao.getStatesByStatesAllExams(userId, scheduledStates).filtered()
                val r = dao.getStatesByStatesAllExams(userId, resolvedStates).filtered().size
                Triple(a, s, r)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c // back-press mid-query is a lifecycle event, not a database failure
            } catch (t: Throwable) {
                // Practically unreachable: the DB is local, uses destructive-migration fallback, the
                // queries are off-main-thread, and this screen is only reachable after a successful read
                // of the same database. Report it rather than swallow it — loud in Crashlytics, quiet
                // for the student — and never render it as "you have no wrong questions".
                com.edumio.app.observability.CrashReporterProvider
                    .get(this@WrongQuestionsActivity).recordException(t)
                null
            }

            if (loaded == null) { showLoadError(); return@launch }
            error.visibility = View.GONE

            val (active, scheduled, resolvedCount) = loaded
            val dueCount = active.size + scheduled.count { now >= it.nextReviewAt }

            findViewById<TextView>(R.id.wpActive).text = "${getString(R.string.wp_active_count)}\n${active.size}"
            findViewById<TextView>(R.id.wpDue).text = "${getString(R.string.wp_due_count)}\n$dueCount"
            findViewById<TextView>(R.id.wpResolved).text = "${getString(R.string.wp_resolved_count)}\n$resolvedCount"

            if (!SolutionAccessPolicy.mayBindSolutionText(access)) {
                // Free: counts only. No stems, no actions — the entitlement layer is the boundary.
                list.removeAllViews()
                empty.visibility = View.GONE
                locked.visibility = View.VISIBLE
                DailyChallengeAnalyticsProvider.get(this@WrongQuestionsActivity).track(DcEvents.SOL_CTA_SHOWN)
                findViewById<Button>(R.id.wpLockedCta).onTap {
                    PremiumPaywallSheet().show(supportFragmentManager, PremiumPaywallSheet.TAG)
                }
                return@launch
            }
            locked.visibility = View.GONE

            // Premium list: active pool + scheduled reviews (resolved history is reflected in counts).
            val pool = active + scheduled
            val stems = try {
                DatabaseProvider.get(this@WrongQuestionsActivity).questionDao()
                    .getQuestionsByIds(pool.map { it.questionId }).associateBy { it.id }
            } catch (_: Throwable) { emptyMap() }
            var rows = pool.map { st ->
                Row(
                    questionId = st.questionId, exam = st.examType, section = st.section, topic = st.topic,
                    state = runCatching { QuestionLearnState.valueOf(st.state) }.getOrDefault(QuestionLearnState.INCORRECT_ONCE),
                    timesIncorrect = st.timesIncorrect, lastSeenAt = st.lastSeenAt, nextReviewAt = st.nextReviewAt,
                    stem = stems[st.questionId]?.questionText ?: "",
                )
            }.filter { it.stem.isNotBlank() }
            rows = when (sort) {
                Sort.DUE -> rows.sortedBy { if (it.state == QuestionLearnState.NEEDS_REVISION) it.nextReviewAt else 0L }
                Sort.RECENT -> rows.sortedByDescending { it.lastSeenAt }
                Sort.MOST_WRONG -> rows.sortedByDescending { it.timesIncorrect }
            }

            list.removeAllViews()
            // EMPTY vs CONTENT only — the ERROR case returned early above.
            empty.visibility = when (
                DailyChallengeHomePresenter.listState(loadFailed = false, itemCount = rows.size)
            ) {
                DailyChallengeHomePresenter.ListState.EMPTY -> View.VISIBLE
                else -> View.GONE
            }
            val inflater = LayoutInflater.from(this@WrongQuestionsActivity)
            val df = DateFormat.getDateInstance(DateFormat.SHORT)
            for (r in rows) {
                val card = inflater.inflate(R.layout.item_wrong_question, list, false)
                val examLabel = runCatching { ExamType.valueOf(r.exam).code }.getOrDefault(r.exam)
                card.findViewById<TextView>(R.id.wqMeta).text =
                    "$examLabel · ${DailyChallengeSectionLabels.label(r.section)} · " +
                        getString(R.string.wp_wrong_times, r.timesIncorrect)
                card.findViewById<TextView>(R.id.wqStem).text = r.stem
                val status = when (r.state) {
                    QuestionLearnState.NEEDS_REVISION ->
                        "${getString(R.string.wp_status_scheduled)} · ${df.format(Date(r.nextReviewAt))}"
                    else -> getString(R.string.wp_status_active)
                }
                card.findViewById<TextView>(R.id.wqStatus).text =
                    "$status · ${df.format(Date(r.lastSeenAt))}"
                val exam = runCatching { ExamType.valueOf(r.exam) }.getOrNull()
                card.findViewById<Button>(R.id.wqRetry).onTap {
                    if (exam != null) startActivity(DailyChallengeReviewActivity.retryIntent(this@WrongQuestionsActivity, exam, r.questionId))
                }
                card.findViewById<Button>(R.id.wqSolution).onTap {
                    if (exam != null) startActivity(SolutionActivity.intent(this@WrongQuestionsActivity, exam, r.questionId))
                }
                list.addView(card)
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, WrongQuestionsActivity::class.java)
    }
}
