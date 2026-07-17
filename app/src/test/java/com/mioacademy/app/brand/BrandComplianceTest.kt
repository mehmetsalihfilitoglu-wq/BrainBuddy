package com.mioacademy.app.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release gate: no historical brand (BrainBuddy / Barjin / MioItalia variants) may appear in any
 * USER-FACING directory. Fails the build if it does, unless the token is in `brand_allowlist.txt`.
 *
 * Scope = what a user or tester can actually see: string/layout/theme resources, launcher-icon vectors,
 * the bundled legal HTML, and the manifest. Deliberately NOT scanned: question banks (frozen; carry
 * internal provenance), internal identifiers, and package names — those are documented technical
 * exceptions in the allowlist / edumio_technical_exception_map.md.
 */
class BrandComplianceTest {

    private val forbidden = listOf("brainbuddy", "brain buddy", "barjin", "mioitalia", "mio italia")

    /** Module dir when run by Gradle; fall back to ./app for other runners. */
    private val moduleRoot: File = when {
        File("src/main/res").isDirectory -> File(".")
        File("app/src/main/res").isDirectory -> File("app")
        else -> File(".")
    }

    private fun scanTargets(): List<File> {
        val res = File(moduleRoot, "src/main/res")
        val out = ArrayList<File>()
        listOf("values", "values-en", "values-it", "values-night", "layout").forEach { d ->
            File(res, d).listFiles { f -> f.extension == "xml" }?.let { out += it }
        }
        listOf("drawable", "mipmap-v26").forEach { d ->
            File(res, d).listFiles { f -> f.name.startsWith("ic_launcher") && f.extension == "xml" }?.let { out += it }
        }
        File(moduleRoot, "src/main/assets").listFiles { f -> f.extension == "html" }?.let { out += it }
        File(moduleRoot, "src/main/AndroidManifest.xml").takeIf { it.exists() }?.let { out += it }
        return out
    }

    private fun allowlistTokens(): Set<String> {
        val f = File(moduleRoot, "../brand_allowlist.txt").takeIf { it.exists() }
            ?: File("brand_allowlist.txt").takeIf { it.exists() }
            ?: return emptySet()
        return f.readLines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") && it.contains("::") }
            .map { it.substringBefore("::").trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    @Test
    fun noHistoricalBrandInUserFacingFiles() {
        val allow = allowlistTokens()
        val targets = scanTargets()
        assertTrue("expected to find user-facing files to scan (moduleRoot=${moduleRoot.absolutePath})", targets.isNotEmpty())
        val violations = ArrayList<String>()
        for (file in targets) {
            file.readLines().forEachIndexed { i, line ->
                val low = line.lowercase()
                for (tok in forbidden) {
                    if (low.contains(tok) && allow.none { low.contains(it) }) {
                        violations += "${file.name}:${i + 1} contains '$tok' -> ${line.trim().take(100)}"
                    }
                }
            }
        }
        assertTrue(
            "Forbidden historical brand found in user-facing files:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun appLabelIsEduMio() {
        val strings = File(moduleRoot, "src/main/res/values/strings.xml")
        assertTrue("strings.xml must exist (${strings.absolutePath})", strings.exists())
        val m = Regex("<string name=\"app_name\">([^<]*)</string>").find(strings.readText())
        assertTrue("app_name string must be present", m != null)
        assertEquals("EDUmio", m!!.groupValues[1].trim())
    }
}
