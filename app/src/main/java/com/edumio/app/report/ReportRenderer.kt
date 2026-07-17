package com.edumio.app.report

/**
 * Turns a real-data report model into a [ReportPayload] (subject + minimal, clean HTML
 * + plain-text fallback). Deliberately understated and easy to read — a report a student
 * looks forward to opening. Honest empty state when there isn't enough data.
 *
 * HTML kept inline-styled and simple so it renders in any email client; a richer
 * server-side template can replace this later behind the same [ReportPayload].
 */
object ReportRenderer {

    private const val ACCENT = "#0E7C66"

    fun renderWeekly(d: WeeklyReportData, recipient: String?): ReportPayload {
        val subject = "📈 Haftalık Öğrenme Raporun — ${d.areaLabel}"
        if (!d.hasEnoughData) return notEnough(ReportType.WEEKLY, subject, recipient)

        val rows = buildList {
            add("Bu hafta çözülen soru" to withDelta(d.questionsSolved, d.questionsDelta))
            add("Doğruluk" to (d.accuracy?.let { pct(it) + accDelta(d.accuracy, d.accuracyPrevWeek) } ?: "—"))
            add("Aktif çalışma günü" to "${d.activeStudyDays} / 7")
            add("Seri" to "${d.streakDays} gün")
            add("Kazanılan XP" to "${d.xpEarned}")
            add("Ustalaşılan soru" to "${d.questionsMastered}")
            add("Tekrar bekleyen" to "${d.questionsUnderReview}")
            d.readinessScore?.let { add("Sınav hazırlık skoru" to (pct0(it) + readinessDelta(d.readinessChange))) }
            d.strongestTopic?.let { add("En güçlü konu" to "${it.first} (${pct(it.second)})") }
            d.weakestTopic?.let { add("Gelişim alanı" to "${it.first} (${pct(it.second)})") }
            if (d.mostImprovedTopic != null && d.mostImprovedDelta > 0)
                add("En çok gelişen" to "${d.mostImprovedTopic} (+${d.mostImprovedDelta} puan)")
            d.journeyMilestone?.let { add("Son kilometre taşı" to it) }
        }
        val heat = heatmapLine(d.heatmapLast7)
        return payload(ReportType.WEEKLY, subject, "Bu haftaki öğrenme özetin", rows, heat,
            d.nextWeekRecommendation, d.studyTimeMinutes, recipient)
    }

    fun renderMonthly(d: MonthlyReportData, recipient: String?): ReportPayload {
        val subject = "🗓️ Aylık Öğrenme Raporun — ${d.areaLabel}"
        if (!d.hasEnoughData) return notEnough(ReportType.MONTHLY, subject, recipient)

        val rows = buildList {
            add("Bu ay çözülen soru" to withDelta(d.questionsSolved, d.questionsDelta))
            add("Doğruluk" to (d.accuracy?.let { pct(it) } ?: "—"))
            add("Aktif çalışma günü" to "${d.activeStudyDays}")
            add("Ustalaşılan soru" to "${d.questionsMastered}")
            add("Tekrar düzeltmesi" to "${d.reviewCorrections}")
            d.readinessScore?.let { add("Sınav hazırlık skoru" to (pct0(it) + readinessDelta(d.readinessChange))) }
            if (d.bestImprovementTopic != null && d.bestImprovementDelta > 0)
                add("En çok gelişen" to "${d.bestImprovementTopic} (+${d.bestImprovementDelta} puan)")
            if (d.weakestAreas.isNotEmpty()) add("Gelişim alanların" to d.weakestAreas.joinToString(", "))
            if (d.journeyMilestones.isNotEmpty()) add("Kilometre taşların" to d.journeyMilestones.joinToString(", "))
        }
        return payload(ReportType.MONTHLY, subject, "Bu ayki öğrenme özetin", rows, null,
            d.nextMonthRecommendation, d.studyTimeMinutes, recipient)
    }

    // ── formatting helpers ──────────────────────────────────────────────────────

    private fun pct(v: Int) = "%$v"
    private fun pct0(v: Int) = "$v / 100"
    private fun withDelta(v: Int, delta: Int) = if (delta != 0) "$v (${signed(delta)} vs geçen)" else "$v"
    private fun accDelta(cur: Int?, prev: Int?) = if (cur != null && prev != null && cur != prev) " (${signed(cur - prev)})" else ""
    private fun readinessDelta(change: Int?) = if (change != null && change != 0) " (${signed(change)})" else ""
    private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

    private fun heatmapLine(counts: List<Int>): String? {
        if (counts.all { it == 0 }) return null
        val blocks = counts.joinToString(" ") { c ->
            when { c == 0 -> "▁"; c < 5 -> "▃"; c < 10 -> "▅"; else -> "▇" }
        }
        return "Son 7 gün: $blocks"
    }

    private fun notEnough(type: ReportType, subject: String, recipient: String?): ReportPayload {
        val msg = "Bu dönem için henüz yeterli veri yok. Birkaç test çöz, bir sonraki raporun " +
            "gerçek verilerinle dolu gelsin."
        val html = wrapHtml("Rapor", "<p style=\"color:#444\">$msg</p>")
        return ReportPayload(type, subject, html, msg, recipient)
    }

    private fun payload(
        type: ReportType, subject: String, heading: String,
        rows: List<Pair<String, String>>, heatLine: String?,
        recommendation: String, studyTimeMinutes: Int?, recipient: String?
    ): ReportPayload {
        val sb = StringBuilder()
        val tb = StringBuilder()
        rows.forEach { (k, v) ->
            sb.append("<tr><td style=\"padding:8px 0;color:#666\">$k</td>")
              .append("<td style=\"padding:8px 0;text-align:right;font-weight:600;color:#111\">$v</td></tr>")
            tb.append("• $k: $v\n")
        }
        heatLine?.let { sb.append("<tr><td colspan=\"2\" style=\"padding:12px 0;color:#111\">$it</td></tr>"); tb.append("$it\n") }
        val timeLine = studyTimeMinutes?.let { "$it dk" } ?: "Çalışma süresi henüz ölçülmüyor"

        val body = """
            <h2 style="color:$ACCENT;margin:0 0 4px">$heading</h2>
            <table style="width:100%;border-collapse:collapse;font-size:15px">$sb</table>
            <p style="margin:16px 0 4px;color:#888;font-size:13px">$timeLine</p>
            <div style="margin-top:16px;padding:14px 16px;background:#F1F8F5;border-radius:12px;color:#0E5C4C">
              <strong>Önerimiz:</strong> $recommendation
            </div>
        """.trimIndent()

        val text = buildString {
            append(heading).append("\n\n").append(tb)
            append("\n").append(timeLine).append("\n\nÖnerimiz: ").append(recommendation)
        }
        return ReportPayload(type, subject, wrapHtml("EDUmio", body), text, recipient)
    }

    private fun wrapHtml(title: String, inner: String): String = """
        <html><body style="margin:0;background:#FafBfC;font-family:-apple-system,Segoe UI,Roboto,sans-serif">
          <div style="max-width:520px;margin:0 auto;padding:24px">
            <div style="font-size:13px;letter-spacing:.14em;color:$ACCENT;font-weight:700">MIOITALIA</div>
            <div style="background:#fff;border:1px solid #EEE;border-radius:16px;padding:20px;margin-top:12px">$inner</div>
            <p style="color:#AAA;font-size:12px;margin-top:16px">Bu rapor yalnızca senin gerçek çalışma verilerinden oluşturuldu.</p>
          </div>
        </body></html>
    """.trimIndent()
}
