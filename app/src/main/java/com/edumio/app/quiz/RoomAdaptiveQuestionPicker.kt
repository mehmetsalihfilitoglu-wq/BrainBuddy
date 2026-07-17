package com.edumio.app.quiz

import com.edumio.app.db.HistoryDao
import com.edumio.app.db.QuestionHistoryEntity
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Room-based adaptive question picker.
 * 20 soru: 8 (%40) due+wrong, 12 (%60) fresh.
 * Aynı soru aynı testte tekrar etmez.
 */
class RoomAdaptiveQuestionPicker(
    private val historyDao: HistoryDao,
    private val getGlobalTestIndex: () -> Int,
    private val getQuestionIdsFromLastNTests: (String, Int) -> Set<String>
) {

    private val now get() = System.currentTimeMillis()
    private val longAgoMs = TimeUnit.DAYS.toMillis(3)

    fun pick(
        pool: List<Question>,
        testSize: Int,
        profileId: String = "default",
        excludeIds: Set<String> = emptySet()
    ): List<Question> = runBlocking {
        if (pool.isEmpty()) return@runBlocking emptyList()
        val exclude = excludeIds.toMutableSet()
        val poolIds = pool.map { it.id }
        val historyMap: Map<String, QuestionHistoryEntity> = historyDao.getByIds(profileId, poolIds).associateBy { it.questionId }
        val threeDaysMs = TimeUnit.DAYS.toMillis(3)
        fun h(id: String) = historyMap[id]
        fun dueAt(ent: QuestionHistoryEntity?) = (ent?.lastAnsweredAt ?: 0L) + threeDaysMs
        fun lastWasWrong(ent: QuestionHistoryEntity?) = (ent?.lastResult ?: 1) == 0
        fun seenCount(ent: QuestionHistoryEntity?) = (ent?.correctCount ?: 0) + (ent?.wrongCount ?: 0)
        fun wrongTotal(ent: QuestionHistoryEntity?) = ent?.wrongCount ?: 0

        val inLast5Tests = getQuestionIdsFromLastNTests(profileId, 5)

        val dueWrongPool = pool.filter { q ->
            val ent = h(q.id)
            q.id !in exclude && ent != null && dueAt(ent) <= now && lastWasWrong(ent)
        }
        val reinforcementPool = pool.filter { q ->
            val ent = h(q.id)
            q.id !in exclude && ent != null && dueAt(ent) > now && lastWasWrong(ent)
        }
        val freshPool = pool.filter { q ->
            val ent = h(q.id)
            q.id !in exclude &&
            q.id !in dueWrongPool.map { it.id } &&
            q.id !in reinforcementPool.map { it.id } && (
                (ent == null || seenCount(ent) == 0) ||
                (now - (ent?.lastAnsweredAt ?: 0L) >= longAgoMs)
            )
        }

        val dueWrongSorted = dueWrongPool.sortedWith(
            compareByDescending<Question> { wrongTotal(h(it.id)) }
                .thenBy { h(it.id)?.lastAnsweredAt ?: 0L }
        )
        val reinforcementSorted = reinforcementPool.sortedWith(
            compareByDescending<Question> { wrongTotal(h(it.id)) }
                .thenBy { h(it.id)?.lastAnsweredAt ?: 0L }
        )
        val freshSorted = freshPool.sortedWith(
            compareBy<Question> { seenCount(h(it.id)) > 0 }
                .thenBy { it.id in inLast5Tests }
                .thenBy { h(it.id)?.lastAnsweredAt ?: 0L }
                .thenBy { seenCount(h(it.id)) }
        )

        val pickWrongCount = (testSize * 0.4).toInt().coerceAtLeast(0)
        val pickWrong = dueWrongSorted.take(pickWrongCount).toMutableList()
        if (pickWrong.size < pickWrongCount) {
            val need = pickWrongCount - pickWrong.size
            pickWrong.addAll(reinforcementSorted.filter { it.id !in pickWrong.map { q -> q.id } }.take(need))
        }

        val wrongIds = pickWrong.map { it.id }.toSet()
        var pickFresh = freshSorted.filter { it.id !in wrongIds }.take(testSize - pickWrongCount).toMutableList()

        if (pickFresh.size < testSize - pickWrongCount) {
            val need = testSize - pickWrongCount - pickFresh.size
            val alreadyPicked = wrongIds + pickFresh.map { it.id }.toSet()
            // First pass: prefer non-recent to avoid repeats in fallback.
            val nonRecentFallback = pool
                .filter { it.id !in alreadyPicked && it.id !in inLast5Tests }
                .sortedBy { h(it.id)?.lastAnsweredAt ?: 0L }
            pickFresh.addAll(nonRecentFallback.take(need))
            // Last resort: allow recent only when pool is genuinely exhausted.
            if (pickFresh.size < testSize - pickWrongCount) {
                val alreadyPicked2 = wrongIds + pickFresh.map { it.id }.toSet()
                val recentFallback = pool
                    .filter { it.id !in alreadyPicked2 }
                    .sortedBy { h(it.id)?.lastAnsweredAt ?: 0L }
                pickFresh.addAll(recentFallback.take(testSize - pickWrongCount - pickFresh.size))
            }
        }

        val finalSet = (pickWrong + pickFresh).distinctBy { it.id }
        if (finalSet.size > testSize) {
            finalSet.take(testSize).shuffled()
        } else {
            finalSet.shuffled()
        }
    }
}
