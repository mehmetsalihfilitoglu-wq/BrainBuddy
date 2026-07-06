package com.mioacademy.app.core

import android.content.Context

/**
 * Provides the short Italian motivation line shown on Home.
 *
 * Tone target: a high-school / graduate student preparing for Italian
 * university admission (IMAT / TOLC / TIL / SAT) — and a parent glancing at
 * the screen. Warm and encouraging, but never childish or corporate.
 * Themes rotate across study discipline, exam prep, the Italy journey,
 * language learning and quiet self-belief.
 *
 * The line changes on every Home open (not once per calendar day), advancing
 * through a shuffled-feeling sequence and never repeating the previous one.
 */
object ItalianMomentProvider {

    data class Moment(val italian: String, val turkish: String)

    private const val PREFS = "bb_moment_prefs"
    private const val KEY_INDEX = "moment_index"

    private val moments = listOf(
        // ── Study discipline ──
        Moment("Ogni giorno un passo avanti.", "Her gün bir adım ileri."),
        Moment("La costanza batte il talento.", "İstikrar, yeteneği yener."),
        Moment("Piano piano si va lontano.", "Sabırla gidilen yol, uzağa varır."),
        Moment("Studia oggi, scegli domani.", "Bugün çalış, yarın seç."),
        Moment("I piccoli sforzi diventano grandi risultati.", "Küçük çabalar, büyük sonuçlara dönüşür."),
        Moment("La disciplina costruisce la libertà.", "Disiplin, özgürlüğü inşa eder."),
        // ── Exam preparation ──
        Moment("Ogni errore ti avvicina alla risposta.", "Her hata seni doğru cevaba yaklaştırır."),
        Moment("Preparati oggi, sii pronto domani.", "Bugün hazırlan, yarın hazır ol."),
        Moment("Conosci la domanda, padroneggia la risposta.", "Soruyu tanı, cevaba hâkim ol."),
        Moment("Un test alla volta.", "Her seferinde bir test."),
        Moment("La pratica rende sicuri.", "Pratik, özgüven kazandırır."),
        // ── The Italy journey ──
        Moment("L'Italia premia chi si prepara.", "İtalya, hazırlananı ödüllendirir."),
        Moment("Il tuo posto all'università ti aspetta.", "Üniversitedeki yerin seni bekliyor."),
        Moment("Roma non fu costruita in un giorno.", "Roma bir günde kurulmadı."),
        Moment("Ogni parola italiana è un passo verso il tuo futuro.", "Her İtalyanca kelime, geleceğine bir adım."),
        Moment("Da qui all'Italia, un giorno alla volta.", "Buradan İtalya'ya, günden güne."),
        // ── Language & curiosity ──
        Moment("Impara la lingua, apri le porte.", "Dili öğren, kapıları aç."),
        Moment("La curiosità è la tua migliore alleata.", "Merak, en iyi yol arkadaşındır."),
        Moment("Chi studia, capisce; chi capisce, avanza.", "Çalışan anlar; anlayan ilerler."),
        // ── Quiet self-belief ──
        Moment("Sei più vicino di quanto pensi.", "Düşündüğünden daha yakınsın."),
        Moment("Il futuro appartiene a chi si prepara.", "Gelecek, hazırlananındır."),
        Moment("Fidati del processo.", "Sürece güven."),
        Moment("La strada è lunga, ma tu sei costante.", "Yol uzun, ama sen istikrarlısın."),
        Moment("Un obiettivo chiaro, un passo deciso.", "Net bir hedef, kararlı bir adım.")
    )

    /**
     * Returns the next motivation line, advancing the persisted pointer so a
     * different line appears on each Home open. Never returns the same line
     * twice in a row.
     */
    fun next(context: Context): Moment {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY_INDEX, -1)
        var idx = (last + 1) % moments.size
        // Guard against an accidental repeat if the list size is ever 1.
        if (idx == last && moments.size > 1) idx = (idx + 1) % moments.size
        prefs.edit().putInt(KEY_INDEX, idx).apply()
        return moments[idx]
    }
}
