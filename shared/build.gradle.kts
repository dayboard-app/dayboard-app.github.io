// Everything that is not about being a web page: the domain, the pure logic, and
// the state holders above them.
//
// Deliberately not an application: no `binaries.executable()`, no webpack, and no
// `moduleKind`. This produces klibs, and the module that links them into a bundle
// is the one that decides the output shape.
//
// Plugin versions come from the root build file, which resolves them for the whole
// build. Adding a version here would let the two drift.
plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

kotlin {
    js(IR) {
        // The browser sub-target exists so the root module's webpack build can consume
        // this klib. Nothing here needs a browser, so its test task stays off and the
        // same tests run on Node instead.
        browser {
            testTask { enabled = false }
        }
        nodejs()
    }

    // The JVM target exists for coverage, not for a JVM product. Kover cannot
    // instrument Kotlin/JS, so without a JVM target the coverage floors in
    // CLAUDE.md §7.5 could not be measured at all. It costs nothing at runtime:
    // the web app links the JS klib and never sees this one.
    jvm()

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: these types appear in signatures the root
            // module reads, so consumers need them on their own compile classpath.
            api("org.jetbrains.compose.runtime:runtime:1.11.1")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}
