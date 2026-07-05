package com.mioacademy.app.coach

import android.content.Context
import com.mioacademy.app.core.AnalyticsStore
import com.mioacademy.app.core.GamificationStore
import com.mioacademy.app.league.LeagueStore
import com.mioacademy.app.league.LeagueTier
import java.util.Calendar

/**
 * Wise Coach Personality: calm, motivating, professional home screen greetings.
 * Rotates by time of day, streak, last test result, weak topic, league rank.
 * No back-to-back repetition. Works when user data is missing.
 */
class WiseCoachGreeting(
    private val context: Context,
    private val gam: GamificationStore,
    private val analytics: AnalyticsStore,
    private val leagueStore: LeagueStore
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getGreeting(): String {
        val timeSlot = getTimeSlot()
        val streakBucket = getStreakBucket()
        val testBucket = getLastTestBucket()
        val hasWeakTopic = hasWeakTopic()
        val leagueBucket = getLeagueBucket()

        val candidates = mutableListOf<String>()
        candidates.addAll(genericPool)

        when (timeSlot) {
            TimeSlot.MORNING -> candidates.addAll(timeMorningPool)
            TimeSlot.AFTERNOON -> candidates.addAll(timeAfternoonPool)
            TimeSlot.EVENING -> candidates.addAll(timeEveningPool)
            TimeSlot.NIGHT -> candidates.addAll(timeNightPool)
        }

        when (streakBucket) {
            StreakBucket.ZERO -> candidates.addAll(streakZeroPool)
            StreakBucket.LOW -> candidates.addAll(streakLowPool)
            StreakBucket.MID -> candidates.addAll(streakMidPool)
            StreakBucket.HIGH -> candidates.addAll(streakHighPool)
        }

        when (testBucket) {
            TestBucket.HIGH -> candidates.addAll(testHighPool)
            TestBucket.MEDIUM -> candidates.addAll(testMediumPool)
            TestBucket.LOW -> candidates.addAll(testLowPool)
            TestBucket.NONE -> candidates.addAll(testNonePool)
        }

        if (hasWeakTopic) candidates.addAll(weakTopicPool)

        when (leagueBucket) {
            LeagueBucket.LOW -> candidates.addAll(leagueLowPool)
            LeagueBucket.MID -> candidates.addAll(leagueMidPool)
            LeagueBucket.HIGH -> candidates.addAll(leagueHighPool)
        }

        val lastShown = prefs.getString(KEY_LAST_GREETING, null)
        val filtered = candidates.filter { it != lastShown }
        val pool = if (filtered.isEmpty()) genericPool.filter { it != lastShown } else filtered
        val finalPool = if (pool.isEmpty()) genericPool else pool

        val picked = finalPool[(System.currentTimeMillis() % finalPool.size).toInt()]
        prefs.edit().putString(KEY_LAST_GREETING, picked).apply()
        return picked
    }

    private fun getTimeSlot(): TimeSlot {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 5..11 -> TimeSlot.MORNING
            hour in 12..16 -> TimeSlot.AFTERNOON
            hour in 17..21 -> TimeSlot.EVENING
            else -> TimeSlot.NIGHT
        }
    }

    private fun getStreakBucket(): StreakBucket {
        val streak = gam.streakDays()
        return when {
            streak == 0 -> StreakBucket.ZERO
            streak <= 3 -> StreakBucket.LOW
            streak <= 7 -> StreakBucket.MID
            else -> StreakBucket.HIGH
        }
    }

    private fun getLastTestBucket(): TestBucket {
        val last = analytics.getLastTests(1).firstOrNull() ?: return TestBucket.NONE
        return when {
            last.accuracy >= 80f -> TestBucket.HIGH
            last.accuracy >= 50f -> TestBucket.MEDIUM
            else -> TestBucket.LOW
        }
    }

    private fun hasWeakTopic(): Boolean {
        val weak = analytics.getWeakestTopicsWithCounts(1)
        return weak.isNotEmpty() && weak.first().second.total >= 3 && weak.first().second.accuracy < 60f
    }

    private fun getLeagueBucket(): LeagueBucket {
        val tier = leagueStore.getCurrentTier()
        return when (tier) {
            LeagueTier.BASLANGIC, LeagueTier.BRONZ -> LeagueBucket.LOW
            LeagueTier.GUMUS, LeagueTier.ALTIN -> LeagueBucket.MID
            LeagueTier.ELMAS, LeagueTier.EFSANE -> LeagueBucket.HIGH
        }
    }

    private enum class TimeSlot { MORNING, AFTERNOON, EVENING, NIGHT }
    private enum class StreakBucket { ZERO, LOW, MID, HIGH }
    private enum class TestBucket { HIGH, MEDIUM, LOW, NONE }
    private enum class LeagueBucket { LOW, MID, HIGH }

    companion object {
        private const val PREFS = "bb_wise_coach"
        private const val KEY_LAST_GREETING = "last_greeting"

        // Generic pool – always available, wise mentor tone
        private val genericPool = listOf(
            "Her gün biraz daha ileri.",
            "Bilgi, sabırla büyür.",
            "Küçük adımlar büyük yol alır.",
            "Öğrenmek bir yolculuktur.",
            "Bugün dünden daha iyi ol.",
            "Zihin, antrenmanla güçlenir.",
            "Doğru soru, cevabın yarısıdır.",
            "Disiplin özgürlüktür.",
            "Hata yapmak öğrenmenin parçasıdır.",
            "Her test bir fırsattır.",
            "Odaklanmak başarının anahtarıdır.",
            "Tutarlılık her şeyi değiştirir.",
            "Sabır, başarının sessiz ortağıdır.",
            "Bugün kendine yatırım yap.",
            "Öğrenme bir alışkanlıktır.",
            "Merak, öğrenmenin ateşidir.",
            "Zorluklar seni güçlendirir.",
            "Her soru bir basamaktır.",
            "İlerleme, mükemmellikten önce gelir.",
            "Beyin kas gibi çalışır; antrenman gerekir.",
            "Bugünün çalışması yarının meyvesidir.",
            "Küçük kazanımlar birikir.",
            "Öğrenmeye devam et.",
            "Farkı yaratan günlük alıştırmadır.",
            "Akıl açık, hedef net olsun."
        )

        private val timeMorningPool = listOf(
            "Güne iyi bir başlangıç için ideal zaman.",
            "Sabah zihni taze, öğrenmeye hazır.",
            "Yeni gün, yeni fırsatlar.",
            "Erken başlayan yol alır.",
            "Sabahın sessizliği odak için fırsat.",
            "Gün aydınlandı; zihin de aydınlansın.",
            "Sabah verimliliği en yüksektir."
        )

        private val timeAfternoonPool = listOf(
            "Öğleden sonra konsantrasyon için uygun zaman.",
            "Gün ortası, odaklanma saatidir.",
            "Öğleden sonra pratik yapmak etkilidir.",
            "Öğle sonrası verimli çalışma zamanı.",
            "Gün ortasında kendini geliştir."
        )

        private val timeEveningPool = listOf(
            "Akşam da öğrenmek için güzel bir zaman.",
            "Günün sonunda bile ilerleme mümkün.",
            "Akşam sessizliği odak getirir.",
            "Günü verimli bitirmek tatmin verir.",
            "Akşam pratiği günü taçlandırır."
        )

        private val timeNightPool = listOf(
            "Gece geç saatte bile öğrenmeye devam edenler kazanır.",
            "Gece sakinliği düşünmek için uygundur.",
            "Yatmadan önce kısa bir pratik faydalıdır.",
            "Gece sessizliğinde odaklanmak kolaydır."
        )

        private val streakZeroPool = listOf(
            "Her seri bir günle başlar.",
            "Bugün serinin ilk günü olabilir.",
            "Başlamak yarı başarmaktır.",
            "İlk adım en önemlisidir.",
            "Bugün seriye başlamak için ideal gün.",
            "Sıfırdan başlamak cesaret ister.",
            "Her uzun yol tek adımla başlar."
        )

        private val streakLowPool = listOf(
            "Serini sürdür, momentum kazan.",
            "Seri büyümeye devam ediyor.",
            "Günlük alışkanlık oluşuyor.",
            "Tutarlılık başarının temelidir.",
            "Serini korumak önemli.",
            "Küçük seriler büyük serilere dönüşür."
        )

        private val streakMidPool = listOf(
            "Serin güçlü; devam et.",
            "Bir haftalık seri, alışkanlığın işaretidir.",
            "Tutarlı çalışmanın meyvesini topluyorsun.",
            "Seri, disiplininin kanıtı.",
            "Orta vadede sabır ödüllendirilir."
        )

        private val streakHighPool = listOf(
            "Uzun seri, büyük azmin göstergesi.",
            "Serin seni farklı kılıyor.",
            "Disiplinli çalışmanın karşılığını alıyorsun.",
            "Uzun seri başarının alışkanlığıdır.",
            "Serini korumak, hedefe ulaşmanın yoludur."
        )

        private val testHighPool = listOf(
            "Son performansın çok iyiydi; devam et.",
            "Başarı, düzenli çalışmanın sonucudur.",
            "Yüksek doğruluk, iyi hazırlığın göstergesi.",
            "Son testte gösterdiğin performans takdire şayan.",
            "Doğruluk oranın hedefe uygun.",
            "İyi gidiyorsun; bu tempo sürsün."
        )

        private val testMediumPool = listOf(
            "Son testte orta seviyede kaldın; biraz daha odaklan.",
            "Her test bir öğrenme fırsatıdır.",
            "Orta seviye, gelişme alanı demektir.",
            "Tekrar ve pratik sonucu yükseltir.",
            "Sonuçlar gelişmeye açık; devam et."
        )

        private val testLowPool = listOf(
            "Zorluk geçicidir; pratikle aşılır.",
            "Her hata bir ders içerir.",
            "Düşük sonuç, odaklanma zamanının geldiğini gösterir.",
            "Tekrar, zayıf noktaları güçlendirir.",
            "Zorluklar seni geliştirir.",
            "Bugün tekrar etmek, yarın kazanmaktır."
        )

        private val testNonePool = listOf(
            "İlk test seni bekliyor.",
            "Pratik yapmak bilgiyi pekiştirir.",
            "Başlamak için bugün iyi bir gün.",
            "Henüz test çözmedin; zamanı geldi.",
            "İlk adımı atmak önemli."
        )

        private val weakTopicPool = listOf(
            "Zayıf konuya odaklanmak büyük kazanım sağlar.",
            "Zorlandığın konu, en çok gelişeceğin alandır.",
            "Zayıf noktayı güçlendirmek stratejiktir.",
            "Zor konuya zaman ayırmak değerlidir.",
            "Zayıf konuyu tekrar etmek akıllıca bir tercihtir.",
            "Gelişim, zor alanlarda pratik yapmakla gelir."
        )

        private val leagueLowPool = listOf(
            "Ligde ilerlemek için günlük pratik şart.",
            "Başlangıç seviyesi, yükselişin temelidir.",
            "Her seviye bir basamaktır.",
            "Ligde yükselmek zaman ve çaba ister.",
            "Temel sağlam olunca lig yükselir."
        )

        private val leagueMidPool = listOf(
            "Ligde orta sıralardasın; devam et.",
            "Gümüş ve altın seviyede tutarlılık önemli.",
            "Orta lig, disiplinin meyvesidir.",
            "Ligde ilerleme devam ediyor.",
            "Orta seviye, hedefe yaklaştığını gösterir."
        )

        private val leagueHighPool = listOf(
            "Üst liglerde olmak büyük başarı.",
            "Elmas ve efsane seviyesi az işte değil.",
            "Lig zirvesi, uzun yolculuğun ödülü.",
            "Üst lig, düzenli çalışmanın sonucudur.",
            "Zirvede kalmak da çaba ister."
        )
    }
}
