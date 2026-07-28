package com.edumio.app.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Release gate: ZERO historical brand (BrainBuddy / Brain Buddy / Barjin / MioItalia / Mio Italia /
 * MioAcademy / Mio Academy and lower/upper variants) anywhere in the ACTIVE repository — file
 * contents, file names, and directory names. Fails the build otherwise.
 *
 * Excluded: build outputs, VCS/IDE/tool dirs, and the paths in `brand_allowlist.txt` (the enforcement
 * machinery itself, migration-documentation that must name the removed brands, and the archived
 * pre-EDUmio docs).
 */
class BrandComplianceTest {

    private val forbidden = listOf(
        "brainbuddy", "brain buddy", "barjin", "mioitalia", "mio italia", "mioacademy", "mio academy",
    )
    private val excludedDirs = setOf(
        ".git", ".claude", "build", ".gradle", ".idea", "node_modules", "out", ".kotlin", ".cxx", "captures",
    )
    private val textExt = setOf(
        "kt", "kts", "java", "xml", "json", "md", "html", "htm", "txt", "pro", "gradle", "py",
        "yaml", "yml", "css", "js", "properties", "cfg", "csv", "svg",
    )

    private val repoRoot: File by lazy {
        var d: File? = File(".").absoluteFile
        while (d != null && !File(d, "settings.gradle.kts").exists() && !File(d, ".git").exists()) d = d.parentFile
        d ?: File(".").absoluteFile
    }

    private fun allowPatterns(): List<String> {
        val f = File(repoRoot, "brand_allowlist.txt")
        if (!f.exists()) return emptyList()
        return f.readLines()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") && it.contains("::") }
            .map { it.substringBefore("::").trim() }
            .filter { it.isNotEmpty() }
    }

    @Test
    fun noHistoricalBrandAnywhereInActiveRepo() {
        val allow = allowPatterns()
        val violations = ArrayList<String>()
        val root = repoRoot

        root.walkTopDown()
            .onEnter { dir -> dir.name !in excludedDirs }
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(root).path.replace('\\', '/')
                if (allow.any { rel.contains(it) }) return@forEach
                val relLow = rel.lowercase()
                // 1) path / filename / directory-name check
                for (tok in forbidden) if (relLow.contains(tok)) violations += "PATH  $rel  ('$tok')"
                // 2) content check for text files under a sane size
                if (file.extension.lowercase() in textExt && file.length() in 1..(4L * 1024 * 1024)) {
                    val text = try { file.readText() } catch (_: Throwable) { return@forEach }
                    val low = text.lowercase()
                    for (tok in forbidden) {
                        val i = low.indexOf(tok)
                        if (i >= 0) {
                            val lineNo = low.substring(0, i).count { it == '\n' } + 1
                            violations += "FILE  $rel:$lineNo  ('$tok')"
                        }
                    }
                }
            }

        assertTrue(
            "Forbidden historical brand found in the active repository (${violations.size}):\n" +
                violations.take(60).joinToString("\n") +
                (if (violations.size > 60) "\n...and ${violations.size - 60} more" else ""),
            violations.isEmpty(),
        )
    }

    @Test
    fun appLabelIsEduMio() {
        val strings = File(repoRoot, "app/src/main/res/values/strings.xml")
        assertTrue("strings.xml must exist (${strings.absolutePath})", strings.exists())
        val m = Regex("<string name=\"app_name\">([^<]*)</string>").find(strings.readText())
        assertTrue("app_name string must be present", m != null)
        assertEquals("EDUmio", m!!.groupValues[1].trim())
    }
}
