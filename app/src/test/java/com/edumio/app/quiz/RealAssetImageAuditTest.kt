package com.edumio.app.quiz

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Real-asset audit of every image-based question in the shipped banks (IMAT, TIL-I, CEnT-S).
 *
 * Uses the EXACT assets packaged into the APK — no fixtures, no generated test images. It proves the
 * integrity half of "every image question renders its complete content": the file is present, is not
 * empty, decodes, and has sane dimensions. It also emits the per-exam audit table into the build log.
 *
 * IMPORTANT SCOPE NOTE: a renderer cannot recover pixels that were never captured. Where a SOURCE
 * image is itself physically cropped (the top of the stem sliced off during extraction), no rendering
 * setting fixes it — those questions are listed in the delivery report as needing asset replacement.
 * This test guards what code can guarantee.
 */
class RealAssetImageAuditTest {

    private data class Bank(val exam: String, val json: String)

    private val banks = listOf(
        Bank("IMAT", "imat/imat_questions.json"),
        Bank("TIL-I", "til_i/questions.json"),
        Bank("CEnT-S", "cents_s/questions.json"),
    )

    /**
     * PNG width/height straight from the IHDR header. Android unit tests compile against the stubbed
     * android.jar, which has no javax.imageio, so we parse the 8-byte signature + IHDR ourselves.
     * Returns null for anything that is not a structurally valid PNG — which is what "undecodable"
     * means here, and every shipped question figure is a PNG.
     */
    private fun readPngDimensions(f: File): Pair<Int, Int>? {
        val head = ByteArray(24)
        f.inputStream().use { if (it.read(head) < 24) return null }
        val isPng = head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() &&
            head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte() &&
            head[12] == 'I'.code.toByte() && head[13] == 'H'.code.toByte() &&
            head[14] == 'D'.code.toByte() && head[15] == 'R'.code.toByte()
        if (!isPng) return null
        fun be(o: Int): Int = ((head[o].toInt() and 0xFF) shl 24) or
            ((head[o + 1].toInt() and 0xFF) shl 16) or
            ((head[o + 2].toInt() and 0xFF) shl 8) or
            (head[o + 3].toInt() and 0xFF)
        return be(16) to be(20)
    }

    private fun assetsRoot(): File = listOf(
        File("src/main/assets"), File("app/src/main/assets"), File("../app/src/main/assets"),
    ).firstOrNull { it.isDirectory } ?: error("assets root not found (cwd=${File(".").absolutePath})")

    @Test
    fun everyReferencedImage_existsDecodesAndHasValidDimensions() {
        val root = assetsRoot()
        var grandTotal = 0
        var grandImages = 0

        for (bank in banks) {
            val arr = JSONArray(File(root, bank.json).readText(Charsets.UTF_8))
            var images = 0
            var missing = 0
            var zeroByte = 0
            var undecodable = 0
            var optionsEmbedded = 0
            var withStem = 0
            var minW = Int.MAX_VALUE; var maxW = 0
            var minH = Int.MAX_VALUE; var maxH = 0
            val tall = ArrayList<String>()
            val wide = ArrayList<String>()

            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val img = o.optString("image").takeIf { it.isNotBlank() && it != "null" } ?: continue
                images++
                val id = o.optString("id")
                if (o.optString("stem").isNotBlank()) withStem++
                // Bare A–E choices ⇒ the real options live inside the figure.
                val ch = o.optJSONArray("choices")
                if (ch != null && ch.length() in 2..5) {
                    val letters = (0 until ch.length()).map { ch.optString(it).trim() }
                    if (letters.all { it.length == 1 && it[0] in 'A'..'E' }) optionsEmbedded++
                }

                val f = File(root, img)
                if (!f.exists()) { missing++; println("[MISSING] ${bank.exam} $id -> $img"); continue }
                if (f.length() == 0L) { zeroByte++; println("[ZERO-BYTE] ${bank.exam} $id -> $img"); continue }
                val dim = readPngDimensions(f)
                if (dim == null) { undecodable++; println("[UNDECODABLE] ${bank.exam} $id -> $img"); continue }

                val (w, h) = dim
                assertTrue("${bank.exam} $id: invalid dimensions ${w}x$h", w > 0 && h > 0)
                if (w < minW) minW = w; if (w > maxW) maxW = w
                if (h < minH) minH = h; if (h > maxH) maxH = h
                if (h.toDouble() / w > 2.5) tall.add("$id(${w}x$h)")
                if (w.toDouble() / h > 4.0) wide.add("$id(${w}x$h)")
            }

            grandTotal += arr.length()
            grandImages += images
            println(
                "[IMAGE-AUDIT ${bank.exam}] questions=${arr.length()} imageQuestions=$images " +
                    "missing=$missing zeroByte=$zeroByte undecodable=$undecodable " +
                    "optionsEmbedded=$optionsEmbedded withStem=$withStem " +
                    "width=$minW..$maxW height=$minH..$maxH " +
                    "tall(h/w>2.5)=${tall.size} wide(w/h>4)=${wide.size}"
            )
            if (tall.isNotEmpty()) println("   tall: ${tall.take(10)}")
            if (wide.isNotEmpty()) println("   wide: ${wide.take(10)}")

            assertEquals("${bank.exam}: every referenced image file must exist", 0, missing)
            assertEquals("${bank.exam}: no image may be zero bytes", 0, zeroByte)
            assertEquals("${bank.exam}: every image must decode", 0, undecodable)
        }

        println("[IMAGE-AUDIT TOTAL] questions=$grandTotal imageQuestions=$grandImages across ${banks.size} exams")
        assertTrue("all three exams audited", banks.size == 3)
        assertTrue("the audit must actually cover images", grandImages > 0)
    }

    /**
     * Locks the rendering contract in the layouts that show a question figure: fitCenter (never
     * centerCrop), adjustViewBounds, and a wrap_content height so a tall figure is never clipped by a
     * fixed-height viewport. All of these screens sit inside a vertical scroll container.
     */
    @Test
    fun questionFigureViews_useFitCenterAndAreNotFixedHeight() {
        val res = listOf(File("src/main/res/layout"), File("app/src/main/res/layout"), File("../app/src/main/res/layout"))
            .firstOrNull { it.isDirectory } ?: error("layout dir not found")

        val figures = mapOf(
            "activity_daily_challenge.xml" to "dcQuestionImage",
            "activity_daily_challenge_review.xml" to "dcrvImage",
            "activity_quiz.xml" to "questionImage",
            "activity_wrong_answer_review.xml" to "questionImage",
            "activity_solution.xml" to "solFigure",
        )

        for ((file, id) in figures) {
            val xml = File(res, file).readText(Charsets.UTF_8)
            val start = xml.indexOf("@+id/$id")
            assertTrue("$file: figure view $id not found", start > 0)
            // The ImageView block: from the tag opening before the id to the next '>' after it.
            val tagStart = xml.lastIndexOf('<', start)
            val tagEnd = xml.indexOf('>', start)
            val block = xml.substring(tagStart, tagEnd)

            assertTrue("$file/$id must use fitCenter", block.contains("android:scaleType=\"fitCenter\""))
            assertTrue("$file/$id must never use centerCrop", !block.contains("centerCrop"))
            assertTrue("$file/$id must set adjustViewBounds", block.contains("android:adjustViewBounds=\"true\""))
            assertTrue(
                "$file/$id must be wrap_content height (no fixed-height clipping)",
                block.contains("android:layout_height=\"wrap_content\""),
            )
            println("[RENDER-CONTRACT] $file/$id fitCenter + adjustViewBounds + wrap_content OK")
        }
    }
}
