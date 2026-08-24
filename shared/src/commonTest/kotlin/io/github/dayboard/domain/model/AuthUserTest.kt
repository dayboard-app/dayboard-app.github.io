package io.github.dayboard.domain.model

import io.github.dayboard.domain.repository.AuthFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthUserTest {

    private fun user(emailVerified: Boolean) = AuthUser(
        uid = "u1",
        email = "someone@example.com",
        emailVerified = emailVerified,
    )

    @Test
    fun mayUseApp_requiresAConfirmedEmail() {
        // The whole point of the gate: Firebase would sign both of these in, and
        // the original's backend would sign in neither without confirmation.
        assertTrue(user(emailVerified = true).mayUseApp)
        assertFalse(user(emailVerified = false).mayUseApp)
    }

    @Test
    fun authFailure_carriesItsCode() {
        val failure = AuthFailure("auth/invalid-credential")
        assertEquals("auth/invalid-credential", failure.code)
    }

    @Test
    fun authFailure_mentionsTheCodeInItsMessage() {
        // The message is only ever read in a log or a stack trace, so it has to
        // name the code; the user-facing sentence comes from AuthMessages instead.
        assertTrue(AuthFailure("auth/weak-password").message.orEmpty().contains("auth/weak-password"))
    }

    @Test
    fun authFailure_survivesACodelessFailure() {
        // What a network stack throwing produces: no `code` property to read.
        assertEquals(AuthFailure.UNKNOWN, AuthFailure(AuthFailure.UNKNOWN).code)
        assertEquals("", AuthFailure.UNKNOWN)
    }

    @Test
    fun theAppsOwnCodeCannotCollideWithAnSdkCode() {
        // Firebase codes are all `auth/`-prefixed, so a `dayboard/` prefix keeps
        // the two vocabularies apart while letting them share one mapping.
        assertTrue(AuthFailure.EMAIL_NOT_VERIFIED.startsWith("dayboard/"))
        assertFalse(AuthFailure.EMAIL_NOT_VERIFIED.startsWith("auth/"))
    }
}
