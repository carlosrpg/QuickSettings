import com.android.build.api.artifact.SingleArtifact
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

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
        // Deliberately pre-23: the overlay permission (protection level includes "pre23") and
        // the microphone are then granted at install, so the car needs no adb grants for them.
        // Cost: a one-time permission review and an "older version of Android" notice on first
        // launch. Set this to 30 to drop both, and grant the overlay with appops instead.
        targetSdk = 22
        // Same scheme as AABrowser-Motion. CI passes -PappVersionName=X.Y.Z[-suffix]; local builds
        // mirror it (next patch after the latest vX.Y.Z tag, "-dev") so they install over CI builds.
        // Both are signed with the same key, and a lower versionCode would be rejected as a downgrade.
        val appVersionName = (findProperty("appVersionName") as String?) ?: run {
            val latestTag = runCatching {
                providers.exec {
                    commandLine("git", "tag", "--list", "v*", "--sort=-v:refname")
                    isIgnoreExitValue = true
                }.standardOutput.asText.get()
            }.getOrDefault("").lineSequence().map(String::trim)
                .firstOrNull { Regex("""v\d+\.\d+\.\d+""").matches(it) }
            val (tagMajor, tagMinor, tagPatch) = (latestTag ?: "v0.1.0").removePrefix("v").split('.').map(String::toInt)
            "$tagMajor.$tagMinor.${tagPatch + 1}-dev"
        }
        val semver = appVersionName.substringBefore('-').split('.').mapNotNull(String::toIntOrNull)
        require(semver.size == 3) { "appVersionName must be MAJOR.MINOR.PATCH, got '$appVersionName'" }
        val (major, minor, patch) = semver
        versionCode = major * 1_000_000 + minor * 1_000 + patch
        versionName = appVersionName
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

    // QuickSettings-<version>[_debug].apk in build/renamedApks/<variant>/, as AABrowser-Motion does.
    androidComponents {
        onVariants { variant ->
            val versionLabel = android.defaultConfig.versionName ?: "unknown"
            val suffix = if (variant.buildType == "debug") "_debug" else ""
            val rename = tasks.register<RenameApkTask>("${variant.name}RenameApk") {
                inputDir.set(variant.artifacts.get(SingleArtifact.APK))
                outputDir.set(layout.buildDirectory.dir("renamedApks/${variant.name}"))
                appName.set("QuickSettings")
                versionNameProp.set(versionLabel)
                debugSuffixProp.set(suffix)
            }
            afterEvaluate {
                val assemble = "assemble${variant.name.replaceFirstChar { it.uppercase() }}"
                if (tasks.findByName(assemble) != null) tasks.named(assemble).configure { finalizedBy(rename) }
            }
        }
    }
}

dependencies {
    // jconfig-carbus, prebuilt: scripts/update-carbus.sh rebuilds it from a sibling checkout.
    implementation(files("libs/carbus-0.2.0.aar"))
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

abstract class RenameApkTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val appName: Property<String>

    @get:Input
    abstract val versionNameProp: Property<String>

    @get:Input
    abstract val debugSuffixProp: Property<String>

    @TaskAction
    fun run() {
        val outDir = outputDir.get().asFile
        if (outDir.exists()) outDir.listFiles()?.forEach { it.deleteRecursively() } else outDir.mkdirs()
        inputDir.get().asFile.listFiles()?.filter { it.extension == "apk" }?.forEach { f ->
            val dest = File(outDir, "${appName.get()}-${versionNameProp.get()}${debugSuffixProp.get()}.apk")
            f.copyTo(dest, overwrite = true)
            println("APK Renamed and Copied to: ${dest.absolutePath}")
        }
    }
}
