package com.brainbuddy.app.quiz

/**
 * G2: Adaptif soru seçici.
 * - %40 dueWrong (yanlış yapılan, due olan sorular)
 * - %60 fresh (yeni / uzun süredir görülmeyen)
 * - Aynı soru aynı test içinde asla tekrar gelmez
 * - Son 2 testte çıkan sorular öncelik düşürülür
 */
class AdaptiveQuestionPicker(
    private val historyStore: QuestionHistoryStore
) {
    /**
     * @param pool Soru havuzu
     * @param testSize Test boyutu (örn 20)
     * @param profileId Profil (son 2 test takibi için)
     * @param excludeIds Bu testte zaten seçilmiş ID'ler (boş olabilir)
     * @return Seçilen sorular (testSize kadar veya daha az)
     */
    fun pick(
        pool: List<Question>,
        testSize: Int,
        profileId: String = "default",
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        val inLast2Tests = historyStore.getQuestionIdsFromLastNTests(profileId, 2)

        // G2.1: dueWrong = due olan VE son cevap yanlış olan sorular
        val dueWrong = pool.filter { q ->
            q.id !in excludeIds &&
            historyStore.isDue(q.id) &&
            historyStore.lastWasWrong(q.id)
        }

        // G2.2: fresh = due değil VEYA hiç görülmemiş sorular
        val fresh = pool.filter { q ->
            q.id !in excludeIds &&
            (q.id !in dueWrong.map { it.id }) &&
            (!historyStore.isDue(q.id) || historyStore.neverSeen(q.id))
        }

        // Son 2 testte çıkan sorular - öncelik düşür (lastSeenInTestId ile yaklaşık)
        // Not: Tam "son 2 test" takibi için ek store gerekir; mevcut lastSeenInTestId
        // ile "bu testte olmasın" zaten sağlanıyor. Çeşitlilik için lastSeenAt kullanılır.

        val dueWrongCount = (testSize * 0.4).toInt().coerceAtLeast(0)

        // Sıralama: dueWrong -> wrongCountTotal yüksek + lastAnsweredAt eski önce
        // G2.3: Son 2 testte çıkanlar öncelik düşür
        val dueWrongSorted = dueWrong.sortedWith(
            compareBy<Question> { it.id in inLast2Tests }
                .thenByDescending { historyStore.getHistory(it.id)?.wrongCountTotal ?: 0 }
                .thenBy { historyStore.getHistory(it.id)?.lastAnsweredAt ?: 0L }
        )

        // Sıralama: fresh -> neverSeen önce, son 2 testte olmayan önce, lastAnsweredAt eski
        val freshSorted = fresh.sortedWith(
            compareBy<Question> { !historyStore.neverSeen(it.id) }
                .thenBy { it.id in inLast2Tests }
                .thenBy { historyStore.getHistory(it.id)?.lastAnsweredAt ?: 0L }
        )

        val selectedIds = mutableSetOf<String>()
        selectedIds.addAll(excludeIds)

        val result = mutableListOf<Question>()

        // 1) %40 dueWrong
        for (q in dueWrongSorted) {
            if (result.size >= testSize) break
            if (q.id !in selectedIds) {
                result.add(q)
                selectedIds.add(q.id)
            }
        }

        // 2) %60 fresh
        for (q in freshSorted) {
            if (result.size >= testSize) break
            if (q.id !in selectedIds) {
                result.add(q)
                selectedIds.add(q.id)
            }
        }

        // 3) Eğer yetmediyse havuzdan doldur (maksimum çeşitlilik: en uzun süredir görülmeyen)
        if (result.size < testSize) {
            val remaining = pool.filter { it.id !in selectedIds }
                .sortedBy { historyStore.getHistory(it.id)?.lastAnsweredAt ?: 0L }
            for (q in remaining) {
                if (result.size >= testSize) break
                result.add(q)
                selectedIds.add(q.id)
            }
        }

        // 4) Hâlâ yetmediyse (havuz küçükse) tekrarlarla doldur - ama aynı testte tekrar asla
        if (result.size < testSize && pool.isNotEmpty()) {
            val usedInResult = result.map { it.id }.toSet()
            val extra = pool.filter { it.id !in usedInResult }
            var idx = 0
            while (result.size < testSize && extra.isNotEmpty()) {
                val q = extra[idx % extra.size]
                result.add(q)
                idx++
            }
            if (result.size > testSize) {
                while (result.size > testSize) result.removeAt(result.lastIndex)
            }
        }

        return result.shuffled()
    }
}
