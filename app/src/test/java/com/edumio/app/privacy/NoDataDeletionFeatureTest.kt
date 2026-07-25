package com.edumio.app.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * v1 ships NO user-facing data-deletion capability. There is no account, no cloud sync and no
 * server-side user data, so on-device data is removed by uninstalling the app — and the Google Play
 * Data Safety declaration is filed on that basis.
 *
 * These assertions read the shipped source, so reintroducing a delete action, a delete string, or a
 * privacy-policy claim of an in-app delete fails the build instead of silently contradicting the
 * filed declaration.
 */
class NoDataDeletionFeatureTest {

    private fun root(): File = listOf(File("src/main"), File("app/src/main"), File("../app/src/main"))
        .firstOrNull { it.isDirectory } ?: error("src/main not found (cwd=${File(".").absolutePath})")

    private fun read(rel: String): String {
        val f = File(root(), rel)
        assertTrue("$rel must exist", f.isFile)
        return f.readText(Charsets.UTF_8)
    }

    private fun mainSources(): List<File> =
        File(root(), "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun theDataRightsContractExposesExportOnly() {
        val src = read("java/com/edumio/app/privacy/DataRightsService.kt")
        assertTrue("exportData must remain", src.contains("fun exportData()"))
        for (gone in listOf("deleteLocalData", "deleteCloudData", "deleteAccount", "revokeSessions", "DeletionResult")) {
            assertFalse("DataRightsService must not declare '$gone'", src.contains(gone))
        }
    }

    @Test
    fun theLocalServiceCannotWipeUserData() {
        val src = read("java/com/edumio/app/privacy/LocalDataRightsService.kt")
        // The old implementation enumerated shared_prefs/ and cleared every file. None of that may return.
        for (gone in listOf("shared_prefs", ".clear()", "deleteLocalData", "deleteAccount", "DeletionResult")) {
            assertFalse("LocalDataRightsService must not contain '$gone'", src.contains(gone))
        }
    }

    @Test
    fun noDeletionActionRemainsOnThePrivacyScreen() {
        val src = read("java/com/edumio/app/ui/DataRightsActivity.kt")
        for (gone in listOf("confirmDelete", "performDelete", "AlertDialog", "data_rights_delete", "ACCOUNT_DELETED")) {
            assertFalse("DataRightsActivity must not contain '$gone'", src.contains(gone))
        }
        assertTrue("the export control stays", src.contains("data_rights_export"))
    }

    @Test
    fun noDeletionStringsShipAnywhere() {
        val strings = read("res/values/strings.xml")
        val removed = listOf(
            "data_rights_delete", "data_rights_delete_sub", "data_rights_delete_confirm",
            "data_rights_delete_confirm_yes", "data_rights_deleted", "data_rights_delete_error",
            "data_rights_cancel",
        )
        for (id in removed) {
            assertFalse("string '$id' must not exist", strings.contains("name=\"$id\""))
        }
        // The Settings row that leads to the privacy screen must not advertise deletion either.
        assertFalse(
            "the Settings subtitle must not promise deletion",
            strings.contains("<string name=\"settings_data_rights_sub\">Verilerini dışa aktar veya sil</string>"),
        )
    }

    @Test
    fun noSourceFileReferencesTheRemovedDeletionApi() {
        val banned = listOf("deleteLocalData", "deleteCloudData", "revokeSessions", "DeletionResult", "ACCOUNT_DELETED")
        val offenders = mainSources().flatMap { f ->
            val t = f.readText(Charsets.UTF_8)
            banned.filter { t.contains(it) }.map { "${f.name}: $it" }
        }
        assertTrue("removed deletion API is still referenced: $offenders", offenders.isEmpty())
    }

    @Test
    fun nothingInTheAppWipesTheDatabasesEither() {
        val offenders = mainSources().filter { f ->
            val t = f.readText(Charsets.UTF_8)
            t.contains("clearAllTables") || t.contains("deleteDatabase")
        }.map { it.name }
        assertTrue("no code may wipe the Room databases: $offenders", offenders.isEmpty())
    }

    @Test
    fun theShippedPrivacyPolicyDoesNotClaimAnInAppDelete() {
        for (doc in listOf("assets/privacy_policy_tr.html", "assets/data_usage_tr.html")) {
            val html = read(doc)
            assertFalse("$doc must not name the removed 'Verilerimi Sil' control", html.contains("Verilerimi Sil"))
            assertFalse(
                "$doc must not tell users they can delete their data from within the app",
                html.contains("cihazından kalıcı\nolarak silebilirsin") || html.contains("kalıcı olarak silebilirsin"),
            )
        }
    }
}
