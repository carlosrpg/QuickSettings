plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

import java.util.Properties

val useMock = providers.gradleProperty("qs.mock").map { it.toBoolean() }.getOrElse(true)

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Gradle property, then local.properties, then environment (CI).
fun signingProp(key: String): String? =
    project.findProperty(key) as? String ?: localProps.getProperty(key) ?: System.getenv(key)

android {
    namespace = "com.carlosrpg.cherry.quicksettings"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.carlosrpg.cherry.quicksettings"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = providers.gradleProperty("appVersionName").getOrElse("0.1.0")
        buildConfigField("boolean", "USE_MOCK", useMock.toString())
    }
    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingProp("RELEASE_STORE_FILE") ?: "release.keystore")
            signingProp("RELEASE_STORE_PASSWORD")?.let { storePassword = it }
            signingProp("RELEASE_KEY_ALIAS")?.let { keyAlias = it }
            signingProp("RELEASE_KEY_PASSWORD")?.let { keyPassword = it }
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
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all { it.useJUnitPlatform() }
    }
    lint {
        // The head unit runs Android 11; the app deliberately targets it.
        disable += "ExpiredTargetSdkVersion"
        disable += "OldTargetApi"
    }
}

dependencies {
    implementation(libs.carbus)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
}
