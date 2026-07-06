package com.mioacademy.app.core

import android.content.Context
import com.mioacademy.app.core.exam.AdmissionExamRegistry
import com.mioacademy.app.quiz.SubjectFilter
import com.mioacademy.app.quiz.WrongQuestionPoolStore
import kotlin.math.roundToInt

/**
 * Decides the ONE meaningful next action for the student, so Home never has to
 * ask "what should I study?" — the app already knows.
 *
 * Fully real-data and active-area scoped:
 * - while today's mission is unfinished it hands over a concrete practice action,
 *   targeting the real weakest topic when there is enough data, otherwise a safe
 *   starter from the active exam's subjects;
 * - once the mission is done it surfaces spaced wrong-answer review if reviews
 *   are piling up, otherwise it closes the day calmly.
 *
 * The chosen topic changes as the student's real performance changes, so the
 * next step feels different day to day without any fabricated content.
 */
class NextStepEngine(private val context: Context) {

    enum class Kind { PRACTICE, REVIEW, DONE }

    data class NextStep(
        val title: String,
        val meta: String,
        val actionLabel: String,
        val kind: Kind,
        val subjectFilter: String?,   // for a subject-targeted quiz; null = mixed
        val missionDone: Int,
        val missionTarget: Int
    )

    fun compute(): NextStep {
        val analytics = AnalyticsStore(context)
        val pending = WrongQuestionPoolStore(context).size()
        val mission = DailyMissionManager(context).getTodayMission()
        val missionComplete = mission.testsDone >= mission.testsTarget
        val perTest = QuizPrefs(context).questionsPerSession().coerceAtLeast(1)
        val exam = AdmissionExamRegistry.get(UserGoalPrefs(context).getCareerPath().examType)
        val minutes = (perTest * 0.8).roundToInt().coerceAtLeast(1)

        if (!missionComplete) {
            val weakest = analytics.getWeakestTopicsWithCounts(1)
                .firstOrNull { it.second.total >= ProgressInsights.MIN_TOPIC_QUESTIONS }
            if (weakest != null) {
                return NextStep(
                    title = "$perTest ${weakest.first} sorusu",
                    meta = "~$minutes dakika · Bugünün görevini ilerletir",
                    actionLabel = "Başla",
                    kind = Kind.PRACTICE,
                    subjectFilter = SubjectFilter.forName(weakest.first),
                    missionDone = mission.testsDone, missionTarget = mission.testsTarget
                )
            }
            // No performance data yet → decided, safe starter from the active exam.
            val subject = exam.subjects.firstOrNull()?.displayNameTr
            return NextStep(
                title = if (subject != null) "$perTest $subject sorusu" else "$perTest soruluk kısa test",
                meta = "~$minutes dakika · Bugünün ilk adımı",
                actionLabel = "Başla",
                kind = Kind.PRACTICE,
                subjectFilter = subject?.let { SubjectFilter.forName(it) },
                missionDone = mission.testsDone, missionTarget = mission.testsTarget
            )
        }

        if (pending >= 3) {
            return NextStep(
                title = "$pending yanlış tekrarı",
                meta = "Bugünkü görev tamam · yanlışlarını pekiştir",
                actionLabel = "Tekrara Başla",
                kind = Kind.REVIEW,
                subjectFilter = null,
                missionDone = mission.testsDone, missionTarget = mission.testsTarget
            )
        }
        return NextStep(
            title = "Bugünkü çalışman tamam",
            meta = "Yarın aynı ritmi koru. İstersen ekstra pratik yapabilirsin.",
            actionLabel = "Ekstra Pratik",
            kind = Kind.DONE,
            subjectFilter = null,
            missionDone = mission.testsDone, missionTarget = mission.testsTarget
        )
    }
}
