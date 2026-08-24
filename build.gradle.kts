import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    kotlin("multiplatform") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    kotlin("plugin.compose") version "2.3.20"
    id("org.jetbrains.compose") version "1.11.1"
}

group = "io.github.dayboard"
version = "1.0.0"

kotlin {
    js(IR) {
        // CommonJS rather than the UMD default. The Firebase SDK is only reachable
        // as a module, so the `@JsModule` externals that arrive with authentication
        // would otherwise need a `@JsNonModule` global fallback that does not exist.
        // Set now so that change is a dependency line, not a module-system switch.
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_COMMONJS)
        }
        browser {
            commonWebpackConfig {
                outputFileName = "app.js"
            }
            // Nothing in this module can be tested without a browser and a signed-in
            // session, and there is no fake for either. Everything testable lives in
            // `:shared` and runs on Node and the JVM in seconds, so there is no Karma
            // here and CI needs no browser.
            testTask { enabled = false }
        }

        binaries.executable()
    }

    sourceSets {
        getByName("jsMain") {
            dependencies {
                implementation(project(":shared"))
                implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
                // Compose HTML is Kotlin/JS only and has no multiplatform variant, so
                // it cannot move into `:shared`. It renders real DOM, which is what
                // lets the clone match the original's CSS, text selection, native
                // inputs and scrollbars rather than approximating them on a canvas.
                implementation("org.jetbrains.compose.html:html-core:1.11.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
    }
}
