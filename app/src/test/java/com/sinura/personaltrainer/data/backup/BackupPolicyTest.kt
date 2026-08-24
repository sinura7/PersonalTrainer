package com.sinura.personaltrainer.data.backup

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Source-of-truth parse of the shipping backup policy. The preflight script
 * is the ratchet; this suite fails in the same Gradle lane as everything else.
 */
class BackupPolicyTest {
    @Test
    fun shippingManifestDisablesAutoBackupAndPointsAtBothRuleFiles() {
        val app = load(source("src/main/AndroidManifest.xml"))
            .getElementsByTagName("application")
            .item(0) as Element
        assertTrue(app.getAttributeNS(ANDROID, "allowBackup") == "false")
        assertTrue(app.getAttributeNS(ANDROID, "fullBackupContent") == "@xml/backup_rules")
        assertTrue(app.getAttributeNS(ANDROID, "dataExtractionRules") == "@xml/data_extraction_rules")
    }

    @Test
    fun legacyRulesExcludeEveryNamedStore() {
        assertRulesCover(source("src/main/res/xml/backup_rules.xml"))
    }

    @Test
    fun extractionRulesExcludeCloudAndDeviceTransfer() {
        val doc = load(source("src/main/res/xml/data_extraction_rules.xml"))
        assertTrue(doc.getElementsByTagName("cloud-backup").length == 1)
        assertTrue(doc.getElementsByTagName("device-transfer").length == 1)
        assertRulesCover(source("src/main/res/xml/data_extraction_rules.xml"))
    }

    private fun assertRulesCover(file: File) {
        val xml = file.readText()
        REQUIRED_PATHS.forEach { path ->
            assertTrue("$file must exclude $path", xml.contains("path=\"$path\""))
        }
        REQUIRED_DOMAINS.forEach { domain ->
            assertTrue("$file must exclude domain $domain", xml.contains("domain=\"$domain\""))
        }
    }

    private fun source(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
        )
        return candidates.first { it.isFile }
    }

    private fun load(file: File) =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        val REQUIRED_PATHS = listOf(
            "personal_trainer.db",
            "datastore",
            "safety-snapshots",
            "restore-journal",
            "pre-migration",
            "rest_timer_state.xml",
            "schema_marker.xml",
        )
        val REQUIRED_DOMAINS = listOf("root", "file", "database", "sharedpref", "external")
    }
}
