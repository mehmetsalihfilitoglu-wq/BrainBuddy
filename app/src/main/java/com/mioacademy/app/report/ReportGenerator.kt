package com.mioacademy.app.report

import android.content.Context
import com.mioacademy.app.core.AnalyticsStore
import com.mioacademy.app.core.GamificationStore
import com.mioacademy.app.core.ProfileStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class ReportData(
    val profileName: String,
    val testsCompleted: Int,
    val totalCorrect: Int,
    val totalWrong: Int,
    val totalBlank: Int,
    val accuracyPercent: Int,
    val weakTopics: List<String>,
    val streakDays: Int,
    val xp: Int,
    val level: Int
)

object ReportGenerator {

    fun generateDailyReport(context: Context): Pair<String, String> {
        val data = collectReportData(context, daily = true)
        return htmlReport(data, daily = true) to textReport(data, daily = true)
    }

    fun generateWeeklyReport(context: Context): Pair<String, String> {
        val data = collectReportData(context, daily = false)
        return htmlReport(data, daily = false) to textReport(data, daily = false)
    }

    private fun collectReportData(context: Context, daily: Boolean): ReportData {
        val analytics = AnalyticsStore(context)
        val gam = GamificationStore(context)
        val profile = ProfileStore(context)
        val profileName = profile.getProfile(profile.getCurrentProfileId())?.name ?: "Öğrenci"

        val now = System.currentTimeMillis()
        val start = if (daily) {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            now - 7 * 24 * 60 * 60 * 1000L
        }

        val perfs = analytics.getTestPerformances().filter { it.tsMs >= start }
        val totalCorrect = perfs.sumOf { it.correctCount }
        val totalWrong = perfs.sumOf { it.wrongCount }
        val totalBlank = perfs.sumOf { it.blankCount }
        val total = totalCorrect + totalWrong + totalBlank
        val accuracyPercent = if (total > 0) (100 * totalCorrect / total) else 0

        val weakest = analytics.getWeakestTopicsWithCounts(3).map { it.first }
        return ReportData(
            profileName = profileName,
            testsCompleted = perfs.size,
            totalCorrect = totalCorrect,
            totalWrong = totalWrong,
            totalBlank = totalBlank,
            accuracyPercent = accuracyPercent,
            weakTopics = weakest,
            streakDays = gam.streakDays(),
            xp = gam.xp(),
            level = gam.level()
        )
    }

    private fun htmlReport(d: ReportData, daily: Boolean): String {
        val title = if (daily) "Günlük Rapor" else "Haftalık Rapor"
        val date = SimpleDateFormat("d MMMM yyyy", Locale("tr")).format(Calendar.getInstance().time)
        val weakList = if (d.weakTopics.isEmpty()) "<li>Veri yok</li>" else d.weakTopics.joinToString("") { "<li>$it</li>" }
        return """
<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>$title</title></head><body style="font-family:sans-serif;padding:20px">
<h1>EDUmio - $title</h1>
<p>$date | Profil: ${d.profileName}</p>
<hr>
<h2>Test Özeti</h2>
<ul>
<li>Tamamlanan test: ${d.testsCompleted}</li>
<li>Doğru: ${d.totalCorrect} | Yanlış: ${d.totalWrong} | Boş: ${d.totalBlank}</li>
<li>Başarı: %${d.accuracyPercent}</li>
</ul>
<h2>Geliştirilmesi Gereken Konular</h2>
<ul>$weakList</ul>
<h2>İlerleme</h2>
<p>Seri: ${d.streakDays} gün | XP: ${d.xp} | Seviye: ${d.level}</p>
<hr>
<p style="font-size:11px;color:#888">Bu rapor EDUmio tarafından oluşturuldu. Gizlilik ayarlarından e-posta raporlarını kapatabilirsiniz.</p>
</body></html>
        """.trimIndent()
    }

    private fun textReport(d: ReportData, daily: Boolean): String {
        val title = if (daily) "Günlük Rapor" else "Haftalık Rapor"
        val date = SimpleDateFormat("d MMMM yyyy", Locale("tr")).format(Calendar.getInstance().time)
        val weak = if (d.weakTopics.isEmpty()) "-" else d.weakTopics.joinToString(", ")
        return """
EDUmio - $title
$date | Profil: ${d.profileName}

Test Özeti:
- Tamamlanan: ${d.testsCompleted}
- Doğru: ${d.totalCorrect} | Yanlış: ${d.totalWrong} | Boş: ${d.totalBlank}
- Başarı: %${d.accuracyPercent}

Geliştirilmesi gereken: $weak
Seri: ${d.streakDays} gün | XP: ${d.xp} | Seviye: ${d.level}
        """.trimIndent()
    }
}
