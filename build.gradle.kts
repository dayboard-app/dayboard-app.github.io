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
                // The design system: tokens, primitives, icons, the theme model, the
                // inline-formatting parser. See ARCHITECTURE.md.
                implementation("io.github.bchmsl:keel")
                implementation("org.jetbrains.compose.runtime:runtime:1.11.1")
                // Compose HTML is Kotlin/JS only and has no multiplatform variant, so
                // it cannot move into `:shared`. It renders real DOM, which is what
                // lets the clone match the original's CSS, text selection, native
                // inputs and scrollbars rather than approximating them on a canvas.
                implementation("org.jetbrains.compose.html:html-core:1.11.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                // npm dependencies are only legal in a Kotlin/JS source set, and the
                // `@JsModule` externals that use this are browser-only anyway, so it
                // could not move into `:shared` even if the domain wanted it.
                implementation(npm("firebase", "12.17.0"))
            }
        }
    }
}

/*
 * Take keel's stylesheets into this module's own resources.
 *
 * Not optional, and there is no way to leave it out safely: a Kotlin Multiplatform
 * library's `jsMain/resources` do NOT reach a consumer's distribution on their own -
 * not copied into `jsProcessResources`, not packed into the klib, absent from
 * `build/dist`. See keel/ARCHITECTURE.md, "How the CSS gets there".
 *
 * The failure without this block is silent: an unresolved `var()` falls back to the
 * property's initial value, so the page renders unstyled with a clean console and a
 * green build.
 *
 * The path is `keel/keel/...` because the submodule root is `keel/` and the library
 * module inside it is also named `keel` - the in-repo form keel's own gallery uses,
 * `keel/src/...`, is one level too shallow from here.
 */
tasks.named<Copy>("jsProcessResources") {
    from(rootProject.layout.projectDirectory.dir("keel/keel/src/jsMain/resources"))
}
