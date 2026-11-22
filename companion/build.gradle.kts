import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun prop(key: String): String? =
    project.findProperty(key) as? String ?: localProps.getProperty(key) ?: System.getenv(key)

// The phone side of phone control: a WebView around the page the car serves, which finds the
// car on the Wi-Fi and falls back to the relay. docs/companion-api.md.
android {
    namespace = "com.carlosrpg.cherry.companion"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.carlosrpg.cherry.companion"
        minSdk = 26
        targetSdk = 36
        // Released with the car app, under the same version (CI passes -PappVersionName).
        val appVersionName = (findProperty("appVersionName") as String?) ?: "0.1.0"
        val (major, minor, patch) = appVersionName.substringBefore('-').split('.').map(String::toInt)
        versionCode = major * 1_000_000 + minor * 1_000 + patch
        versionName = appVersionName
        // Same relay as the car build; empty = Wi-Fi only.
        buildConfigField("String", "RELAY_URL", "\"${prop("qs.relayUrl").orEmpty()}\"")
    }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(prop("RELEASE_STORE_FILE") ?: "release.keystore")
            prop("RELEASE_STORE_PASSWORD")?.let { storePassword = it }
            prop("RELEASE_KEY_ALIAS")?.let { keyAlias = it }
            prop("RELEASE_KEY_PASSWORD")?.let { keyPassword = it }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
    testImplementation(libs.org.json)
}
