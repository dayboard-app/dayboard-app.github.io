package io.github.dayboard.presentation

import io.github.dayboard.domain.repository.AuthFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The Firebase error-code spellings, pinned.
 *
 * [AuthMessages] recognises a failure by the Firebase **JS** SDK's code string.
 * Nothing in the types enforces those spellings, so a rename upstream - or a second
 * platform whose SDK says `ERROR_INVALID_CREDENTIAL` - would compile, run, and
 * quietly answer every failure with the generic sentence. That is not something a
 * screenshot would catch, so the list below is the contract.
 *
 * The assertions come in pairs on purpose: one that a code produces its own
 * sentence, and one that it does not produce the fallback. Without the second,
 * deleting the entire mapping would still leave a green suite.
 */
class AuthMessagesTest {

    private val generic = AuthMessages.forCode("")

    private val actionable = listOf(
        // All three credential codes deliberately share one sentence: which one
        // Firebase sends depends on the project's email-enumeration setting.
        "auth/invalid-credential" to "Invalid login credentials.",
        "auth/wrong-password" to "Invalid login credentials.",
        "auth/user-not-found" to "Invalid login credentials.",
        "auth/invalid-email" to "That email address is not valid.",
        "auth/email-already-in-use" to "That email is already registered.",
        "auth/weak-password" to "Password must be at least 6 characters.",
        "auth/too-many-requests" to "Too many attempts. Try again in a moment.",
        "auth/network-request-failed" to "Could not reach the network.",
        "auth/user-disabled" to "That account has been disabled.",
        "auth/unauthorized-domain" to "This site is not on the Firebase authorized-domain list.",
        "auth/operation-not-allowed" to
            "Email and password sign-in is not enabled for this project.",
    )

    @Test
    fun everyActionableCodeGetsItsOwnSentence() {
        actionable.forEach { (code, expected) ->
            assertEquals(expected, AuthMessages.forCode(code), code)
        }
    }

    @Test
    fun noActionableCodeReadsAsTheGenericFailure() {
        actionable.forEach { (code, _) ->
            assertNotEquals(generic, AuthMessages.forCode(code), code)
        }
    }

    @Test
    fun anUnrecognisedCodeReadsAsTheGenericFailure() {
        listOf(
            "",
            "auth/internal-error",
            // The spelling an un-normalised Android SDK would hand over.
            "ERROR_INVALID_CREDENTIAL",
            // Retired in Firebase 10.7.0; if this ever maps, the mapping is stale.
            "auth/invalid-login-credentials",
        ).forEach { code ->
            assertEquals(generic, AuthMessages.forCode(code), "code \"$code\"")
        }
    }

    @Test
    fun theEmailConfirmationGateHasItsOwnSentence() {
        assertEquals(
            AuthMessages.EMAIL_NOT_VERIFIED,
            AuthMessages.forCode(AuthFailure.EMAIL_NOT_VERIFIED),
        )
        assertNotEquals(generic, AuthMessages.forCode(AuthFailure.EMAIL_NOT_VERIFIED))
    }

    @Test
    fun codesAreMatchedWithOrWithoutTheAuthPrefix() {
        // The prefix is stripped by some wrappers and kept by others; both must land.
        assertEquals(
            AuthMessages.forCode("auth/weak-password"),
            AuthMessages.forCode("weak-password"),
        )
    }

    @Test
    fun everySentenceIsAFinishedSentence() {
        // These go straight under the form with no further formatting.
        (actionable.map { it.second } + generic + AuthMessages.EMAIL_NOT_VERIFIED).forEach {
            assertTrue(it.isNotBlank(), "blank message")
            assertTrue(it.first().isUpperCase(), "should start capitalised: \"$it\"")
            assertTrue(it.last() == '.' || it.last() == '!', "should end in punctuation: \"$it\"")
        }
    }

    @Test
    fun theCredentialCodesAreIndistinguishable() {
        // Wording them differently would leak whether an address is registered,
        // which is the whole point of Firebase's enumeration protection.
        val sentences =
            listOf("auth/invalid-credential", "auth/wrong-password", "auth/user-not-found")
            .map { AuthMessages.forCode(it) }
            .toSet()
        assertEquals(1, sentences.size, "credential failures must not be distinguishable")
    }
}
