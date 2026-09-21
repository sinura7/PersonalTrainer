package com.sinura.personaltrainer.data.auth

import com.sinura.personaltrainer.domain.AccountAuthError
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountAuthErrorMappingTest {
    @Test
    fun invalidLoginCredentialsMapToWrongPasswordCopy() {
        val error = IllegalStateException("Invalid login credentials")
            .toAccountAuthError(configured = true)
        assertEquals(AccountAuthError.InvalidCredentials, error)
    }

    @Test
    fun unconfiguredPortMapsToNotConfigured() {
        val error = UnconfiguredAccountAuth.NotConfiguredException
            .toAccountAuthError(configured = false)
        assertEquals(AccountAuthError.NotConfigured, error)
    }
}
