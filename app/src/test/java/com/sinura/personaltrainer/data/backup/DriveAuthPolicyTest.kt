package com.sinura.personaltrainer.data.backup

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FND-028: Drive backup must not import the deprecated Google Sign-In
 * package. Email comes from Drive About. Scope stays drive.file.
 */
class DriveAuthPolicyTest {
    @Test
    fun driveAuthClientDoesNotImportGoogleSignIn() {
        val source = source("src/main/java/com/sinura/personaltrainer/data/backup/DriveAuthClient.kt")
            .readText()
        assertFalse(source.contains("com.google.android.gms.auth.api.signin"))
        assertFalse(source.contains("GoogleSignIn"))
        assertFalse(source.contains("toGoogleSignInAccount"))
        assertFalse(source.contains("getSignInClient"))
        assertTrue(source.contains("AuthorizationClient") || source.contains("getAuthorizationClient"))
        assertTrue(source.contains("drive.file"))
        assertTrue(source.contains("clearToken"))
        assertTrue(source.contains("revokeAccess"))
    }

    @Test
    fun catalogPinsPlayServicesAuth216() {
        val catalog = source("gradle/libs.versions.toml").readText()
        val match = Regex("""^playServicesAuth\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
            .find(catalog)
        assertEquals("21.6.0", checkNotNull(match).groupValues[1])
    }

    @Test
    fun aboutJsonYieldsEmailAndIgnoresJunk() {
        assertEquals(
            "owner@example.com",
            DriveAboutJson.parseAccountEmail(
                """{"user":{"emailAddress":"owner@example.com","displayName":"Owner"}}""",
            ),
        )
        assertNull(DriveAboutJson.parseAccountEmail("""{"kind":"drive#about"}"""))
        assertNull(DriveAboutJson.parseAccountEmail("not-json"))
        assertNull(DriveAboutJson.parseAccountEmail("""{"user":{"emailAddress":"  "}}"""))
    }

    private fun source(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../$relative"),
        )
        return candidates.first { it.isFile }
    }
}
