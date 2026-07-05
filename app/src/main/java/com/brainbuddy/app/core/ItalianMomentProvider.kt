package com.brainbuddy.app.core

import java.util.Calendar

object ItalianMomentProvider {

    data class Moment(val italian: String, val turkish: String)

    private val moments = listOf(
        Moment("Ce la farai!", "Başarabilirsin!"),
        Moment("Ogni giorno un passo avanti.", "Her gün bir adım ileri."),
        Moment("Non mollare mai.", "Asla pes etme."),
        Moment("Il futuro appartiene a chi crede.", "Gelecek, inananlarındır."),
        Moment("Piano piano si va lontano.", "Ağır ağır uzağa gidilir."),
        Moment("Ogni errore è una lezione.", "Her hata bir derstir."),
        Moment("La perseveranza è la chiave.", "Azim, başarının anahtarıdır."),
        Moment("L'Italia ti aspetta.", "İtalya seni bekliyor."),
        Moment("Studia oggi, vinci domani.", "Bugün çalış, yarın kazan."),
        Moment("Sii curioso, sii coraggioso.", "Meraklı ol, cesur ol."),
        Moment("Il successo si costruisce ogni giorno.", "Başarı her gün inşa edilir."),
        Moment("Credi in te stesso.", "Kendine inan."),
        Moment("La conoscenza è potere.", "Bilgi güçtür."),
        Moment("Il tuo sogno è possibile.", "Hayalin gerçekleşebilir."),
        Moment("Vai avanti, non guardare indietro.", "İleri git, arkana bakma."),
        Moment("Roma non fu costruita in un giorno.", "Roma bir günde inşa edilmedi."),
        Moment("Chi studia, conquista.", "Çalışan, fetheder."),
        Moment("Impara, cresci, conquista.", "Öğren, büyü, kazan."),
        Moment("Sei più vicino di quanto pensi.", "Düşündüğünden çok daha yakınsın."),
        Moment("Ogni giorno è una nuova opportunità.", "Her gün yeni bir fırsat."),
        Moment("Lavora duro, sogna in grande.", "Çok çalış, büyük hayal kur."),
        Moment("La strada è lunga, ma tu sei forte.", "Yol uzun, ama sen güçlüsün.")
    )

    fun today(): Moment {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return moments[dayOfYear % moments.size]
    }
}
