package com.brainbuddy.app.quiz

import com.brainbuddy.app.db.HistoryDao
import com.brainbuddy.app.db.QuestionHistoryEntity
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
    private val minTestGap = 3

    fun pick(
        pool: List<Question>,
        testSize: Int,
        profileId: String = "default",
        excludeIds: Set<String> = emptySet()
    ): List<Question> = runBlocking {
        if (pool.isEmpty()) return@runBlocking emptyList()
        val exclude = excludeIds.toMutableSet()
        val poolIds = pool.map { it.id }
        val historyMap: Map<String, QuestionHistoryEntity> = historyDao.getByIds(poolIds).associateBy { it.questionId }
        fun h(id: String) = historyMap[id]

        val globalTestIndex = getGlobalTestIndex()
        val inLast2Tests = getQuestionIdsFromLastNTests(profileId, 2)

        val dueWrongPool = pool.filter { q ->
            q.id !in exclude && (h(q.id)?.let { it.dueAt <= now && it.lastWasWrong } ?: false)
        }
        val reinforcementPool = pool.filter { q ->
            q.id !in exclude && (h(q.id)?.let { it.dueAt > now && it.lastWasWrong } ?: false)
        }
        val freshPool = pool.filter { q ->
            q.id !in exclude &&
            q.id !in dueWrongPool.map { it.id } &&
            q.id !in reinforcementPool.map { it.id } && (
                (h(q.id) == null || h(q.id)!!.seenCount == 0) ||
                (globalTestIndex - (h(q.id)?.lastSeenTestIndex ?: 0) >= minTestGap) ||
                (now - (h(q.id)?.lastAnsweredAt ?: 0L) >= longAgoMs)
            )
        }

        val dueWrongSorted = dueWrongPool.sortedWith(
            compareByDescending<Question> { h(it.id)?.wrongTotal ?: 0 }
                .thenBy { h(it.id)?.lastAnsweredAt ?: 0L }
        )
        val reinforcementSorted = reinforcementPool.sortedWith(
            compareByDescending<Question> { h(it.id)?.wrongTotal ?: 0 }
                .thenBy { h(it.id)?.lastAnsweredAt ?: 0L }
        )
        val freshSorted = freshPool.sortedWith(
            compareBy<Question> { (h(it.id)?.seenCount ?: 0) > 0 }
                .thenBy { it.id in inLast2Tests }
                .thenBy { h(it.id)?.lastAnsweredAt ?: 0L }
                .thenBy { h(it.id)?.seenCount ?: 0 }
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
            val fallbackPool = pool.filter { it.id !in wrongIds && it.id !in pickFresh.map { it.id } }
                .sortedBy { h(it.id)?.lastAnsweredAt ?: 0L }
            pickFresh.addAll(fallbackPool.take(testSize - pickWrongCount - pickFresh.size))
        }

        val finalSet = (pickWrong + pickFresh).distinctBy { it.id }
        if (finalSet.size > testSize) {
            finalSet.take(testSize).shuffled()
        } else {
            finalSet.shuffled()
        }
    }
}
