package io.github.dayboard.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The emulator switch, pinned.
 *
 * The failure this prevents is asymmetric. Getting it wrong locally means the
 * emulators are ignored, which is obvious within seconds. Getting it wrong the
 * other way ships a site that tries to reach `localhost:9099` from a stranger's
 * browser, and nothing catches that until after a deploy.
 */
class EnvironmentTest {

    @Test
    fun theDeployedSiteNeverUsesEmulators() {
        assertFalse(shouldUseEmulators("dayboard-app.github.io"))
    }

    @Test
    fun thisMachineUsesEmulators() {
        listOf("localhost", "127.0.0.1", "[::1]").forEach {
            assertTrue(shouldUseEmulators(it), "hostname \"$it\"")
        }
    }

    @Test
    fun aHostnameThatMerelyContainsLocalhostIsNotThisMachine() {
        // Matching loosely would hand a stranger's domain the emulator endpoints.
        listOf(
            "localhost.example.com",
            "staging.localhost",
            "notlocalhost",
            "localhost.attacker.test",
            "127.0.0.1.example.com",
        ).forEach {
            assertFalse(shouldUseEmulators(it), "hostname \"$it\"")
        }
    }

    @Test
    fun anEmptyOrUnknownHostnameIsNotThisMachine() {
        assertFalse(shouldUseEmulators(""))
        assertFalse(shouldUseEmulators("example.com"))
    }
}
