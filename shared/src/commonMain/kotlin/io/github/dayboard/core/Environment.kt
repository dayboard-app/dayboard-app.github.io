package io.github.dayboard.core

/**
 * Whether this page should talk to the local Firebase emulators instead of the
 * real project.
 *
 * The decision is a pure function of the hostname, and it lives here rather than
 * beside the Firebase setup so it can be tested. What it is guarding against is
 * not a local mistake but a deployed one: a build that pointed the live site at
 * `localhost:9099` would fail every sign-in with a connection error, and the
 * failure would only appear after a deploy.
 *
 * The list is exact rather than a prefix or a `contains`. A host like
 * `localhost.example.com` is somebody else's domain, and `staging.localhost` is
 * not this machine either.
 */
fun shouldUseEmulators(hostname: String): Boolean = hostname in LOCAL_HOSTNAMES

/** Every name a browser uses for this machine. `[::1]` is IPv6 loopback. */
private val LOCAL_HOSTNAMES = setOf("localhost", "127.0.0.1", "[::1]")
