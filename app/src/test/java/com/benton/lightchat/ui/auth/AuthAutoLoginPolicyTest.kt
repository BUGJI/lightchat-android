package com.benton.lightchat.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthAutoLoginPolicyTest {

    @Test
    fun deterministicFailuresClearSavedCredentials() {
        listOf(
            "invalid_credentials",
            "invalid_username",
            "missing_fields",
            "account_disabled",
            "account_locked",
        ).forEach { code ->
            assertTrue(code, isDeterministicLoginFailure(code))
        }
    }

    @Test
    fun transientFailuresKeepSavedCredentials() {
        listOf(
            "network_error",
            "http_500",
            "empty_response",
            "token_expired",
            "too_many_requests",
        ).forEach { code ->
            assertFalse(code, isDeterministicLoginFailure(code))
        }
    }
}
