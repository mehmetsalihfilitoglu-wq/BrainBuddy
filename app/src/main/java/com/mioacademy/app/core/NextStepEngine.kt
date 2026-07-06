package com.mioacademy.app.core

import android.content.Context
import com.mioacademy.app.quiz.WrongQuestionPoolStore

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
        val subjectFilter: String?,          // for a subject-targeted quiz; null = mixed
        val missionCategories: Set<String>?, // curated mission category set (premium bias)
        val missionDone: Int,
        val missionTarget: Int
    )

    fun compute(): NextStep {
        val pending = WrongQuestionPoolStore(context).size()
        val mission = DailyMissionManager(context).getTodayMission()
        val missionComplete = mission.testsDone >= mission.testsTarget

        // While the mission is unfinished, the next step IS today's mini exam —
        // a balanced slice of the real entrance-exam blueprint (premium: quietly
        // weighted toward weaker areas). See DailyMissionEngine.
        if (!missionComplete) {
            val plan = DailyMissionEngine(context).plan()
            val meta = if (plan.isPersonalized)
                "${plan.examCode} formatı · ${plan.total} soru · son performansına göre kişiselleştirildi"
            else
                "${plan.examCode} formatı · ${plan.total} soru"
            return NextStep(
                title = "Bugünkü Mini Sınav",
                meta = meta,
                actionLabel = "Sınava Başla",
                kind = Kind.PRACTICE,
                subjectFilter = null,
                missionCategories = plan.executionCategories,
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
                missionCategories = null,
                missionDone = mission.testsDone, missionTarget = mission.testsTarget
            )
        }
        return NextStep(
            title = "Bugünkü çalışman tamam",
            meta = "Yarın aynı ritmi koru. İstersen ekstra pratik yapabilirsin.",
            actionLabel = "Ekstra Pratik",
            kind = Kind.DONE,
            subjectFilter = null,
            missionCategories = null,
            missionDone = mission.testsDone, missionTarget = mission.testsTarget
        )
    }
}
