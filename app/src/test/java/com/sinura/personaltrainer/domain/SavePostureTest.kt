package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavePostureTest {
    @Test
    fun freshInstallHasNoLegacyPosture() {
        assertNull(
            inferLegacySavePosture(
                launchPermissionsAsked = false,
                onboardingComplete = false,
                driveAccountEmail = null,
                accountSignedIn = false,
            ),
        )
    }

    @Test
    fun priorPermissionsImplyLocalPosture() {
        assertEquals(
            SavePosture.LOCAL,
            inferLegacySavePosture(
                launchPermissionsAsked = true,
                onboardingComplete = false,
                driveAccountEmail = null,
                accountSignedIn = false,
            ),
        )
    }

    @Test
    fun signedInAccountWinsOverDrive() {
        assertEquals(
            SavePosture.ACCOUNT,
            inferLegacySavePosture(
                launchPermissionsAsked = false,
                onboardingComplete = false,
                driveAccountEmail = "a@gmail.com",
                accountSignedIn = true,
            ),
        )
    }

    @Test
    fun fromStorageIsCaseInsensitive() {
        assertEquals(SavePosture.ACCOUNT, SavePosture.fromStorage("account"))
        assertEquals(SavePosture.LOCAL, SavePosture.fromStorage("LOCAL"))
    }
}
