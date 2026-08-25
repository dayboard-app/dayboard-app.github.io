rootProject.name = "dayboard"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// The root project is the web app itself, not a `:web` subproject. That keeps the
// GitHub Pages artifact at `build/dist/js/productionExecutable`, which is the path
// the deploy workflow uploads. Renaming the root would move that path silently, and
// `upload-pages-artifact` succeeds on an empty directory — publishing a working-looking
// 404 rather than failing the build.
//
// `:shared` holds everything that is not about being a web page: the domain, the
// state holders, and the pure logic. It has a JVM target so those tests can run
// under Kover, which cannot instrument Kotlin/JS.
include(":shared")

// The shared design system: tokens, primitives, icons, the theme model, and the
// inline-formatting parser. A submodule at `keel/`, included as a composite build
// rather than `include(":keel")` + `projectDir` - the latter would run keel's own
// build script inside this build, where `libs` is THIS project's version catalog,
// and keel's script applies plugins by an alias this project does not declare.
//
// Depend on it with coordinates - `implementation("io.github.bchmsl:keel")` - never
// `project(":keel")`, which resolves to this build's own (nonexistent) project of
// that name rather than being substituted for the included one.
includeBuild("keel")
