package com.mioacademy.app.quiz

import java.util.concurrent.TimeUnit

/**
 * G3: Adaptif soru seçici - 20 soruluk test, kesin oran.
 * - 8 soru (%40): dueWrong (yanlış yapılan + due)
 * - 12 soru (%60): fresh (yeni / uzun süredir görülmeyen / farklı konu)
 * - Aynı soru aynı testte asla tekrar etmez (Set garanti)
 * - Son 2 testte çıkan sorular fresh'te öncelik düşürülür
 */
class AdaptiveQuestionPicker(
    private val historyStore: QuestionHistoryStore
) {
    private val now get() = System.currentTimeMillis()
    private val globalTestIndex get() = historyStore.getGlobalTestIndex()
    private val longAgoMs = TimeUnit.DAYS.toMillis(3)

    /**
     * @param pool Soru havuzu
     * @param testSize Test boyutu (20)
     * @param profileId Profil (son 2 test takibi)
     * @param excludeIds Bu testte zaten seçilmiş ID'ler
     */
    fun pick(
        pool: List<Question>,
        testSize: Int,
        profileId: String = "default",
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        val exclude = excludeIds.toMutableSet()

        // G3: dueWrongPool = dueAt <= now AND lastWasWrong
        val dueWrongPool = pool.filter { q ->
            q.id !in exclude && historyStore.isDue(q.id) && historyStore.lastWasWrong(q.id)
        }

        // reinforcementPool = lastWasWrong ama dueAt > now (dueWrong yetersizse)
        val reinforcementPool = pool.filter { q ->
            q.id !in exclude &&
            !historyStore.isDue(q.id) &&
            historyStore.lastWasWrong(q.id)
        }

        // freshPool = neverSeen OR (uzun süredir görülmedi) OR (globalTestIndex - lastSeenTestIndex >= 3)
        val freshPool = pool.filter { q ->
            q.id !in exclude &&
            q.id !in dueWrongPool.map { it.id } &&
            q.id !in reinforcementPool.map { it.id } &&  // reinforcement only for wrong fill
            (historyStore.neverSeen(q.id) ||
             (globalTestIndex - (historyStore.getHistory(q.id)?.lastSeenTestIndex ?: 0) >= 3) ||
             (now - (historyStore.getHistory(q.id)?.lastAnsweredAt ?: 0L) >= longAgoMs))
        }

        val inLast5Tests = historyStore.getQuestionIdsFromLastNTests(profileId, 5)

        // dueWrong sort: wrongTotal desc, lastAnsweredAt asc
        val dueWrongSorted = dueWrongPool.sortedWith(
            compareByDescending<Question> { historyStore.getHistory(it.id)?.wrongCountTotal ?: 0 }
                .thenBy { historyStore.getHistory(it.id)?.lastAnsweredAt ?: 0L }
        )

        // reinforcement sort: same
        val reinforcementSorted = reinforcementPool.sortedWith(
            compareByDescending<Question> { historyStore.getHistory(it.id)?.wrongCountTotal ?: 0 }
                .thenBy { historyStore.getHistory(it.id)?.lastAnsweredAt ?: 0L }
        )

        // fresh sort: neverSeen first, then NOT in last 2 tests, then lastAnsweredAt asc, then seenCount asc
        val freshSorted = freshPool.sortedWith(
            compareBy<Question> { !historyStore.neverSeen(it.id) }  // neverSeen first
                .thenBy { it.id in inLast5Tests }  // last 2 tests last (deprioritize)
                .thenBy { historyStore.getHistory(it.id)?.lastAnsweredAt ?: 0L }
                .thenBy { historyStore.getHistory(it.id)?.seenCount ?: 0 }
        )

        val pickWrongCount = (testSize * 0.4).toInt().coerceAtLeast(0)  // 8 for testSize=20
        val pickFreshCount = testSize - pickWrongCount  // 12

        val pickWrong = dueWrongSorted.take(pickWrongCount).toMutableList()
        if (pickWrong.size < pickWrongCount) {
            val need = pickWrongCount - pickWrong.size
            val fromReinforcement = reinforcementSorted.filter { it.id !in pickWrong.map { q -> q.id } }.take(need)
            pickWrong.addAll(fromReinforcement)
        }

        val wrongIds = pickWrong.map { it.id }.toSet()
        val pickFresh = freshSorted.filter { it.id !in wrongIds }.take(pickFreshCount).toMutableList()

        if (pickFresh.size < pickFreshCount) {
            val need = pickFreshCount - pickFresh.size
            val alreadyPicked = wrongIds + pickFresh.map { it.id }.toSet()
            // First pass: exclude questions seen in last 2 tests (anti-repeat enforced).
            val nonRecentFallback = pool
                .filter { it.id !in alreadyPicked && it.id !in inLast5Tests }
                .sortedBy { historyStore.getHistory(it.id)?.lastAnsweredAt ?: 0L }
            pickFresh.addAll(nonRecentFallback.take(need))
            // Last resort only: allow recent questions if pool is truly exhausted.
            if (pickFresh.size < pickFreshCount) {
                val alreadyPicked2 = wrongIds + pickFresh.map { it.id }.toSet()
                val recentFallback = pool
                    .filter { it.id !in alreadyPicked2 }
                    .sortedBy { historyStore.getHistory(it.id)?.lastAnsweredAt ?: 0L }
                pickFresh.addAll(recentFallback.take(pickFreshCount - pickFresh.size))
            }
        }

        val finalSet = (pickWrong + pickFresh).distinctBy { it.id }
        if (finalSet.size > testSize) {
            return finalSet.take(testSize).shuffled()
        }
        return finalSet.shuffled()
    }
}
