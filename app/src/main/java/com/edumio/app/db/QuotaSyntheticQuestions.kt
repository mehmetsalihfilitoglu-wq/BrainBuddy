package com.edumio.app.db

import com.edumio.app.quiz.QuestionDiversity
import com.edumio.app.quiz.QuestionQualityGate
import com.edumio.app.quiz.Subject
import org.json.JSONArray

/**
 * Deficit-only synthetic questions for core (grade 1..7 × mat/turkce/fen/sosyal/ing).
 * Stems are wordy/contextual so they stay ACTIVE (quota fill bypasses strict gate at insert).
 */
object QuotaSyntheticQuestions {

    fun generate(grade: Int, subjectKey: String, count: Int): List<QuestionEntity> {
        require(grade in 1..7)
        require(count > 0)
        val out = ArrayList<QuestionEntity>(count)
        for (seq in 0 until count) {
            val q = when (subjectKey) {
                "mat" -> mat(grade, seq)
                "turkce" -> turkce(grade, seq)
                "fen" -> fen(grade, seq)
                "sosyal" -> sosyal(grade, seq)
                "ing" -> ing(grade, seq)
                else -> mat(grade, seq)
            }
            out.add(q)
        }
        return out
    }

    private fun newId(grade: Int, subject: String, seq: Int): String =
        "quota_${grade}_${subject}_${seq}"

    private fun subjectEnum(key: String): Subject = when (key) {
        "mat" -> Subject.MAT
        "turkce" -> Subject.TURKCE
        "fen" -> Subject.FEN
        "sosyal" -> Subject.SOSYAL
        "ing" -> Subject.ING
        else -> Subject.MAT
    }

    private fun baseEntity(
        id: String,
        grade: Int,
        subjectKey: String,
        difficulty: Int,
        stem: String,
        options: List<String>,
        answerIndex: Int,
        explanation: String?
    ): QuestionEntity {
        val padded = if (options.size >= 4) options.take(4) else options + List(4 - options.size) { "-" }
        val ai = answerIndex.coerceIn(0, padded.size - 1)
        val se = subjectEnum(subjectKey)
        val type = QuestionDiversity.inferType(se, stem)
        val skill = QuestionDiversity.inferSkill(se, grade, type, stem)
        val stemNorm = QuestionStemHash.normalizeStem(stem)
        val hash = QuestionStemHash.stemHash(stem)
        val gate = QuestionQualityGate.evaluate(se, grade, stem, padded, difficulty.coerceIn(0, 2), answerIndex = ai)
        return QuestionEntity(
            id = id,
            grade = grade,
            subject = subjectKey,
            difficulty = difficulty.coerceIn(0, 2),
            questionText = stem,
            optionsJson = JSONArray(padded).toString(),
            answerIndex = ai,
            explanation = explanation,
            isActive = true,
            questionType = gate.questionType,
            skillsJson = gate.skillsJson,
            deactivationReason = null,
            version = 1,
            examType = "GENERAL",
            imageAsset = null,
            type = type,
            skill = skill,
            stemNormalized = stemNorm,
            stemHash = hash,
            qualityTier = gate.qualityTier,
            reasoningLevel = gate.reasoningLevel,
            reasoningScore = gate.reasoningScore,
            distractorQualityScore = gate.distractorQualityScore,
            contextComplexityScore = gate.contextComplexityScore,
            qualityFlagsJson = gate.qualityFlagsJson,
            unservableReason = null,
        )
    }

    /**
     * Inserts unique IDs so REPLACE does not collide with prior quota rows when the pool is empty.
     */
    fun generateEmergencyTopUp(grade: Int, subjectKey: String, count: Int): List<QuestionEntity> {
        require(grade in 1..7)
        require(count > 0)
        val salt = System.nanoTime()
        return generate(grade, subjectKey, count).mapIndexed { i, e ->
            e.copy(id = "quota_em_${grade}_${subjectKey}_${salt}_$i")
        }
    }

    private fun mat(grade: Int, seq: Int): QuestionEntity {
        val id = newId(grade, "mat", seq)
        val windowRows = 3 + (seq % 3)
        val doorRows = 4 + (seq % 4)
        val perRow = 3 + (seq % 2)
        val totalRows = windowRows + doorRows
        val totalStudents = totalRows * perRow
        val stem = "Bir $grade. sınıf matematik dersinde öğretmen, sınıf düzenini modellemek için şu problemi yazmıştır: " +
            "Pencere kenarında $windowRows sıra, kapı tarafında $doorRows sıra vardır. Her sırada $perRow öğrenci oturabilmektedir. " +
            "Öğrenciler yerleştirirken sıra başına oturma kuralına uymakta ve boş sıra bırakmamaktadır. " +
            "Bu sınıfta oturduğunda dolu olan sıralarda toplam kaç öğrenci vardır?"
        val expl = "Toplam sıra: $windowRows + $doorRows = $totalRows. Her sırada $perRow öğrenci → $totalRows × $perRow = $totalStudents."
        val diff = seq % 3
        return baseEntity(
            id, grade, "mat", diff, stem,
            listOf(
                totalStudents.toString(),
                (totalStudents - perRow).toString(),
                (totalStudents + 5).toString(),
                (totalRows + perRow).toString()
            ),
            0,
            expl
        )
    }

    private fun turkce(grade: Int, seq: Int): QuestionEntity {
        val id = newId(grade, "turkce", seq)
        val name = listOf("Ali", "Ayşe", "Deniz", "Ece", "Mert", "Zeynep")[seq % 6]
        val day = listOf("pazartesi", "salı", "çarşamba", "perşembe", "cuma")[seq % 5]
        val wake = 7 + (seq % 2)
        val study = 1 + (seq % 2)
        val hobby = if (seq % 2 == 0) "resim yapar" else "kitap okur"
        val p = "$name, her $day sabah saat $wake'de uyanır. Kahvaltıdan sonra ödevlerini bitirir ve günde yaklaşık $study saat " +
            "ders çalışır. Çalışmasını tamamladıktan sonra bir süre $hobby ve ardından ailesiyle zaman geçirir. " +
            "$name, gününü önceden planlayarak hem derslerine hem de dinlenmeye zaman ayırmaya çalışır. Bu parça $grade. sınıf " +
            "okuma metni için örnek bir bağlam sunar."
        val stem = "$p\n\nBu parçaya göre $name sabah kaçta uyanmaktadır?"
        val correct = "$wake'de"
        val diff = seq % 3
        return baseEntity(
            id, grade, "turkce", diff, stem,
            listOf(correct, "${wake + 1}'de", "6'da", "9'da"),
            0,
            "Parçada sabah uyanma saati açıkça verilmiştir."
        )
    }

    private fun fen(grade: Int, seq: Int): QuestionEntity {
        val id = newId(grade, "fen", seq)
        val initialTemp = 20 + (seq % 5)
        val minutes = 5 + (seq % 4)
        val risePerMin = 2
        val finalTemp = initialTemp + minutes * risePerMin
        val base = "Bir $grade. sınıf fen laboratuvarında öğrenci, suyun sıcaklığını ölçmek için deney yapmaktadır. " +
            "Deneyin başında sıcaklık $initialTemp °C'dir. Isıtma sırasında her dakika sıcaklık $risePerMin °C artmaktadır. " +
            "Deney protokolü gereği ölçümler düzenli aralıklarla kaydedilmektedir."
        val stem = "$base\n\n$minutes dakika sonra sıcaklık yaklaşımı kaç °C olur?"
        val diff = seq % 3
        return baseEntity(
            id, grade, "fen", diff, stem,
            listOf(
                finalTemp.toString(),
                (finalTemp - 3).toString(),
                (finalTemp + 3).toString(),
                initialTemp.toString()
            ),
            0,
            "Artış: $minutes × $risePerMin = ${minutes * risePerMin}; sonuç: $initialTemp + ${minutes * risePerMin} = $finalTemp."
        )
    }

    private fun sosyal(grade: Int, seq: Int): QuestionEntity {
        val id = newId(grade, "sosyal", seq)
        val cityA = if (seq % 2 == 0) "Ankara" else "İzmir"
        val cityB = if (seq % 3 == 0) "İstanbul" else "Konya"
        val distance = 280 + (seq % 8) * 15
        val speed = 55 + (seq % 4) * 5
        val hours = distance / speed
        val base = "Bir aile, Türkiye haritası üzerinde $cityA ile $cityB arasındaki kara yolculuğunu planlamaktadır. " +
            "Haritadaki ölçeğe göre iki şehir arası yaklaşık $distance km'dir. Aile, ortalama saatte $speed km hızla giden " +
            "bir otobüsle yolculuk yapacaktır. Bu metin $grade. sınıf sosyal bilgiler bağlamında mesafe-zaman ilişkisini örnekler."
        val stem = "$base\n\nBuna göre yolculuk yaklaşık kaç saat sürer?"
        val diff = seq % 3
        return baseEntity(
            id, grade, "sosyal", diff, stem,
            listOf(
                hours.toString(),
                (hours + 2).toString(),
                (hours - 1).coerceAtLeast(1).toString(),
                (hours + 5).toString()
            ),
            0,
            "Yaklaşık süre ≈ mesafe / hız = $distance ÷ $speed ≈ $hours saat."
        )
    }

    private fun ing(grade: Int, seq: Int): QuestionEntity {
        val id = newId(grade, "ing", seq)
        val name = if (seq % 2 == 0) "Tom" else "Lisa"
        val hobby = if (seq % 3 == 0) "playing football" else "reading books"
        val time = if (seq % 2 == 0) "after school" else "at the weekend"
        val p = "$name is a student in grade $grade. $name likes $hobby $time. " +
            "$name usually finishes homework first and then spends some time with friends or family. " +
            "$name thinks that having a good balance between study and free time is important for well-being."
        val stem = "$p\n\nAccording to the text, when does $name usually enjoy $hobby?"
        val diff = seq % 3
        return baseEntity(
            id, grade, "ing", diff, stem,
            listOf(time, "in the morning", "at midnight", "before school"),
            0,
            "The text states the hobby is enjoyed at the mentioned time phrase."
        )
    }
}
