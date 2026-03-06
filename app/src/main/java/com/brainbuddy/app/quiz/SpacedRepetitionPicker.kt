package com.brainbuddy.app.quiz

import com.brainbuddy.app.db.HistoryDao
import com.brainbuddy.app.db.QuestionMapper
import com.brainbuddy.app.db.RoomQuizDataStore
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Spaced repetition question picker.
 * - Yanlış: 3 gün sonra tekrar (%60 öncelik)
 * - Doğru: correctCount=1→30g, 2→45g, >=3→60g (cooldown; tekrar sorulmasın)
 * - Subject quota: Mat 5, Tr 5, Fen 4, Sos 3, Eng 3
 * - Aynı test içinde aynı soru asla gelmesin
 * - relax: 60->45->30->14->7 gün
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

    /** DB subject values: mat/turkce/fen/sosyal/ing/inkilap/din (DbSeeder & QuestionDao format) */
    private fun toDbSubject(s: Subject): String = when (s) {
        Subject.MAT -> "mat"
        Subject.TURKCE -> "turkce"
        Subject.FEN -> "fen"
        Subject.SOSYAL -> "sosyal"
        Subject.ING -> "ing"
        Subject.INKILAP -> "inkilap"
        Subject.DIN -> "din"
    }

    private fun cooldownDays(correctCount: Int): Int = when {
        correctCount >= 3 -> 60
        correctCount == 2 -> 45
        correctCount == 1 -> 30
        else -> 0
    }

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

            val subjectIdsInPool = subjectPool.map { it.id }.toSet()

            val dueWrongIds = historyDao.getDueWrongQuestionIds(
                userId = userId,
                subject = dbSubject,
                nowMinus3Days = nowMinus3Days,
                limit = (wrongQuota + newQuota) * 3
            ).filter { it in subjectIdsInPool && it !in usedIds }
            val wrongPicked = dueWrongIds.take(wrongQuota)

            val recentlyCorrect = historyDao.getRecentlyCorrectForSubject(userId, dbSubject)
            val cooldownIds: Set<String> = recentlyCorrect.mapNotNull { h ->
                val days = cooldownDays(h.correctCount)
                if (days <= 0) {
                    null
                } else {
                    val cutoff = now - TimeUnit.DAYS.toMillis(days.toLong())
                    if (h.lastAnsweredAt > cutoff) h.questionId else null
                }
            }.toSet()

            fun safeIds(ids: Collection<String>): List<String> =
                if (ids.isEmpty()) listOf("__none__") else ids.toList()

            val excludeBase = (usedIds + wrongPicked)

            val newIds = historyDao.getNewQuestionIds(
                userId = userId,
                subject = dbSubject,
                excludeIds = safeIds(excludeBase),
                limit = (newQuota * 10).coerceAtLeast(50)
            ).shuffled()

            val notRecentlyCorrectIds = historyDao.getNotRecentlyCorrectQuestionIds(
                userId = userId,
                subject = dbSubject,
                excludeIds = safeIds(cooldownIds + excludeBase),
                limit = (newQuota * 10).coerceAtLeast(50)
            ).shuffled()

            val candidateNew = (newIds + notRecentlyCorrectIds)
                .distinct()
                .filter { it in subjectIdsInPool && it !in usedIds && it !in wrongPicked && it !in cooldownIds }

            var newPicked = candidateNew.take(newQuota)
            if (newPicked.size < newQuota) {
                val fallbackPool = subjectPool.map { it.id }
                    .filter { it !in usedIds && it !in wrongPicked && it !in cooldownIds }
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
