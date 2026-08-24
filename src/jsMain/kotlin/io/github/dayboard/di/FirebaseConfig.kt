package io.github.dayboard.di

/**
 * The Firebase web app configuration for the `dayboard-app` project.
 *
 * These are publishable client values, not secrets. Firebase documents them as
 * safe to ship in client code, exactly as the original app shipped its Supabase
 * publishable key. The security boundary is `firebase/firestore.rules`, which
 * allows a signed-in account to touch only its own subtree; knowing these values
 * grants nothing without an account.
 *
 * Keeping them in source rather than in CI secrets is deliberate: the GitHub
 * Pages build is a plain `jsBrowserDistribution` with no substitution step, and a
 * secret that must reach the browser is not a secret anyway.
 *
 * The live project state that these mirror is recorded in `firebase/README.md`.
 */
object FirebaseConfig {
    const val API_KEY: String = "AIzaSyD2hagNO0moNun79fje7tmBmH1h9bvnv4c"
    const val AUTH_DOMAIN: String = "dayboard-app.firebaseapp.com"
    const val PROJECT_ID: String = "dayboard-app"
    const val STORAGE_BUCKET: String = "dayboard-app.firebasestorage.app"
    const val MESSAGING_SENDER_ID: String = "333709728827"
    const val APP_ID: String = "1:333709728827:web:1ca7949f2ab638b70d901e"
}
