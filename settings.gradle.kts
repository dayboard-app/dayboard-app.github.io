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
