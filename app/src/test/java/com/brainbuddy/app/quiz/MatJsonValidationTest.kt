package com.brainbuddy.app.quiz

import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import java.io.File

/**
 * Validates that all MAT LGS JSON files parse correctly with org.json (same as QuestionPackImporter).
 */
class MatJsonValidationTest {

    @Test
    fun allMatJsonFilesParseSuccessfully() {
        val cwd = File(System.getProperty("user.dir"))
        val matDir = sequenceOf(
            File(cwd, "app/src/main/assets/lgs_exam/mat"),
            File(cwd, "src/main/assets/lgs_exam/mat"),
            File(cwd.parentFile, "app/src/main/assets/lgs_exam/mat"),
            File(cwd, "lgs_exam/mat")
        ).firstOrNull { it.exists() }
        if (matDir == null) {
            Assert.fail("MAT assets dir not found. CWD: ${cwd.absolutePath}")
            return
        }
        val jsonFiles = matDir.listFiles()?.filter { it.extension == "json" }?.sortedBy { it.name } ?: emptyList()
        Assert.assertTrue("No JSON files found in mat dir", jsonFiles.isNotEmpty())

        val failures = mutableListOf<String>()
        for (f in jsonFiles) {
            try {
                val content = f.readText(Charsets.UTF_8)
                JSONObject(content)
            } catch (e: Exception) {
                failures.add("${f.name}: ${e.message}")
            }
        }
        if (failures.isNotEmpty()) {
            Assert.fail("JSON parse failures:\n" + failures.joinToString("\n"))
        }
    }
}
