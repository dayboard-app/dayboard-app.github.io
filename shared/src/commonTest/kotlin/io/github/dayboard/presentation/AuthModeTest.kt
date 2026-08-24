package io.github.dayboard.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthModeTest {

    @Test
    fun signIn_wordingMatchesTheOriginal() {
        with(AuthMode.SignIn) {
            assertEquals("Welcome back", heading)
            assertEquals("Sign in to sync your tasks & timer", subtext)
            assertEquals("Sign in", submitLabel)
            assertEquals("Don't have an account?", togglePrompt)
            assertEquals("Sign up", toggleAction)
        }
    }

    @Test
    fun signUp_wordingMatchesTheOriginal() {
        with(AuthMode.SignUp) {
            assertEquals("Create account", heading)
            assertEquals("Sign up to save your progress", subtext)
            assertEquals("Sign up", submitLabel)
            assertEquals("Already have an account?", togglePrompt)
            assertEquals("Sign in", toggleAction)
        }
    }

    @Test
    fun toggled_swapsTheModeAndIsItsOwnInverse() {
        assertEquals(AuthMode.SignUp, AuthMode.SignIn.toggled())
        assertEquals(AuthMode.SignIn, AuthMode.SignUp.toggled())
        AuthMode.entries.forEach { mode ->
            assertEquals(mode, mode.toggled().toggled(), "$mode round trip")
        }
    }

    @Test
    fun theToggleActionNamesTheOtherModesButton() {
        // The link says where it takes you, so it must read as the other mode's
        // submit label. Getting these crossed is the easy mistake here.
        AuthMode.entries.forEach { mode ->
            assertEquals(mode.toggled().submitLabel, mode.toggleAction, "$mode")
        }
    }

    @Test
    fun defaultIsSignIn() {
        assertEquals(AuthMode.SignIn, AuthMode.Default)
    }

    @Test
    fun theTwoModesAreDistinguishableEverywhereTheyAreShown() {
        val a = AuthMode.SignIn
        val b = AuthMode.SignUp
        assertNotEquals(a.heading, b.heading)
        assertNotEquals(a.subtext, b.subtext)
        assertNotEquals(a.submitLabel, b.submitLabel)
        assertNotEquals(a.togglePrompt, b.togglePrompt)
        assertTrue(AuthMode.entries.all { it.heading.isNotBlank() && it.subtext.isNotBlank() })
    }
}
