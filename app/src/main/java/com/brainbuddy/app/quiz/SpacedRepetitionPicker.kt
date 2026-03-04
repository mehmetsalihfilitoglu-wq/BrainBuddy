package com.brainbuddy.app.quiz

import com.brainbuddy.app.db.HistoryDao
import com.brainbuddy.app.db.QuestionMapper
import com.brainbuddy.app.db.RoomQuizDataStore
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Spaced repetition question picker.
 * - %60 due wrong (öncelikli), %40 new / not recently-correct
 * - Subject quota: Mat 5, Tr 5, Fen 4, Sos 3, Eng 3
 * - Aynı test içinde aynı soru asla gelmesin
 * - Cooldown: correctCount 1->30d, 2->45d, >=3->60d; relax: 60->45->30->14->7
 */
class SpacedRepetitionPicker(
    private val historyDao: HistoryDao,
    private val roomStore: RoomQuizDataStore,
    private val getQuestionIdsFromLastNTests: (String, Int) -> Set<String>
) {
    private val now get() = System.currentTimeMillis()
    private val threeDaysMs = TimeUnit.DAYS.toMillis(3)

    /** Subject quota: Mat 5, Tr 5, Fen 4, Sos 3, Eng 3 -> total 20 */
    private val subjectQuota: Map<Subject, Int> = mapOf(
        Subject.MAT to 5,
        Subject.TURKCE to 5,
        Subject.FEN to 4,
        Subject.SOSYAL to 3,
        Subject.ING to 3
    )

    /** DB subject values: math, tr, en, fen, sosyal (DbSeeder format) */
    private fun toDbSubject(s: Subject): String = when (s) {
        Subject.MAT -> "math"
        Subject.TURKCE -> "tr"
        Subject.FEN -> "fen"
        Subject.SOSYAL -> "sosyal"
        Subject.ING -> "en"
    }

    private fun cooldownDays(correctCount: Int): Int = when {
        correctCount >= 3 -> 60
        correctCount == 2 -> 45
        correctCount == 1 -> 30
        else -> 0
    }

    private val relaxSequence = listOf(60, 45, 30, 14, 7)

    fun pick(
        pool: List<Question>,
        testSize: Int,
        userId: String,
        excludeIds: Set<String> = emptySet()
    ): List<Question> = runBlocking {
        if (pool.isEmpty()) return@runBlocking emptyList()
        val poolBySubject = pool.groupBy { it.subject }
        val usedIds = excludeIds.toMutableSet()
        val result = mutableListOf<Question>()

        for ((subject, quota) in subjectQuota) {
            if (quota <= 0) continue
            val subjectPool = (poolBySubject[subject] ?: emptyList()).filter { it.id !in usedIds }
            if (subjectPool.isEmpty()) continue

            val dbSubject = toDbSubject(subject)
            val nowMinus3Days = now - threeDaysMs

            val wrongQuota = (quota * 0.6).toInt().coerceAtLeast(0)
            val newQuota = quota - wrongQuota

            val dueWrongIds = historyDao.getDueWrongQuestionIds(userId, dbSubject, nowMinus3Days, wrongQuota + newQuota + 10)
                .filter { it in subjectPool.map { q -> q.id } && it !in usedIds }
            val wrongPicked = dueWrongIds.take(wrongQuota)

            val recentlyCorrect = historyDao.getRecentlyCorrectForSubject(userId, dbSubject)
            /** Relax: use override days; normally cooldown per correctCount. In cooldown = lastAnsweredAt > now - days */
            fun recentlyCorrectIdsWithCooldown(relaxDays: Int): Set<String> {
                val cutoff = now - TimeUnit.DAYS.toMillis(relaxDays.toLong())
                return recentlyCorrect
                    .filter { it.correctCount >= 1 && it.lastAnsweredAt > cutoff }
                    .map { it.questionId }
                    .toSet()
            }

            var newPicked = emptyList<String>()
            for (cooldownDays in relaxSequence) {
                val recentlyCorrectIds = recentlyCorrectIdsWithCooldown(cooldownDays)
                val excludeForNew = (usedIds + wrongPicked + newPicked).toList()
                val safeExclude = if (excludeForNew.isEmpty()) listOf("") else excludeForNew
                val newIds = historyDao.getNewQuestionIds(userId, dbSubject, safeExclude, newQuota + 10)
                val notInCooldown = historyDao.getNotRecentlyCorrectQuestionIds(
                    userId, dbSubject,
                    (recentlyCorrectIds + excludeForNew).distinct().let { if (it.isEmpty()) listOf("") else it },
                    newQuota + 10
                )
                val candidateNew = (newIds + notInCooldown).distinct()
                    .filter { it in subjectPool.map { q -> q.id } && it !in usedIds && it !in wrongPicked }
                    .filter { it !in recentlyCorrectIds }
                newPicked = candidateNew.take(newQuota)
                if (newPicked.size >= newQuota) break
            }
            if (newPicked.size < newQuota) {
                val fallbackPool = subjectPool.map { it.id }.filter { it !in usedIds && it !in wrongPicked }
                newPicked = (newPicked + fallbackPool).distinct().take(newQuota)
            }

            val subjectPicked = (wrongPicked + newPicked).distinct().take(quota)
            usedIds.addAll(subjectPicked)
            val questions = roomStore.getQuestionsByIds(subjectPicked)
            result.addAll(questions)
        }

        result.shuffled()
    }
}
