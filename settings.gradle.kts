pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "QuickSettings"
include(":app")

// Builds against a jconfig-carbus checkout so library changes are picked up without publishing:
// the sibling folder locally, or ./jconfig-carbus where CI checks it out. Without either, the
// JitPack release is used.
val carbusCheckout = listOf("jconfig-carbus", "../jconfig-carbus")
    .map { file(it) }
    .firstOrNull { it.resolve("settings.gradle.kts").exists() }
if (carbusCheckout != null) {
    includeBuild(carbusCheckout) {
        dependencySubstitution {
            substitute(module("com.github.carlosrpg.jconfig-carbus:carbus")).using(project(":carbus"))
        }
    }
}
