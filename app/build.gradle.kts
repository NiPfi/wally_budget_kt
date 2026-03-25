import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

fun resolveAdbExecutable(): String {
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { input ->
            localProperties.load(input)
        }
    }

    val sdkDir = sequenceOf(
        localProperties.getProperty("sdk.dir"),
        System.getenv("ANDROID_SDK_ROOT"),
        System.getenv("ANDROID_HOME")
    )
        .filterNotNull()
        .map { path -> File(path) }
        .firstOrNull { it.exists() }
        ?: throw org.gradle.api.GradleException(
            "Unable to locate the Android SDK for connected test device checks."
        )

    val adbName = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        "adb.exe"
    } else {
        "adb"
    }
    val adbFile = sdkDir.resolve("platform-tools").resolve(adbName)
    if (!adbFile.exists()) {
        throw org.gradle.api.GradleException(
            "Unable to locate adb at ${adbFile.absolutePath} for connected test device checks."
        )
    }
    return adbFile.absolutePath
}

fun ensureOnlyEmulatorDevicesForConnectedTests() {
    val process = ProcessBuilder(resolveAdbExecutable(), "devices", "-l")
        .redirectErrorStream(true)
        .start()
    val adbOutput = process.inputStream.bufferedReader().use { it.readText() }

    if (process.waitFor() != 0) {
        throw org.gradle.api.GradleException("Failed to query adb devices before running connected tests.")
    }

    val physicalDevices = adbOutput
        .lineSequence()
        .map(String::trim)
        .filter { line ->
            line.isNotBlank() &&
                !line.startsWith("List of devices attached") &&
                "\tdevice" in line &&
                !line.startsWith("emulator-")
        }
        .toList()

    if (physicalDevices.isNotEmpty()) {
        throw org.gradle.api.GradleException(
            buildString {
                appendLine("Refusing to run connected Android tests on physical devices.")
                appendLine("Disconnect authorized phones/tablets and retry.")
                appendLine("Detected physical devices:")
                physicalDevices.forEach { appendLine(" - $it") }
            }.trimEnd()
        )
    }
}

fun Project.gradlePropertyOrEnv(name: String): String? {
    return providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull
}

val releaseStoreFilePath = project.gradlePropertyOrEnv("WALLYBUDGET_RELEASE_STORE_FILE")
val releaseStorePassword = project.gradlePropertyOrEnv("WALLYBUDGET_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = project.gradlePropertyOrEnv("WALLYBUDGET_RELEASE_KEY_ALIAS")
val releaseKeyPassword = project.gradlePropertyOrEnv("WALLYBUDGET_RELEASE_KEY_PASSWORD")
val enableAbiSplits = providers.gradleProperty("wallybudget.enableAbiSplits")
    .map(String::toBoolean)
    .orElse(false)
    .get()
val hasReleaseSigning =
    !releaseStoreFilePath.isNullOrBlank() &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "net.loeu.wallybudget"
    compileSdk = 36

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "net.loeu.wallybudget"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.version"
            )
        }
    }
    splits {
        abi {
            isEnable = enableAbiSplits
            if (enableAbiSplits) {
                reset()
                include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                isUniversalApk = false
            }
        }
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    ignoreFailures = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline.set(rootProject.file("config/detekt/baseline.xml"))
    basePath.set(rootProject.layout.projectDirectory)
}

tasks.withType<Detekt>().configureEach {
    jvmTarget.set("17")
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}

tasks.withType<DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("17")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.startup.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)
    implementation(libs.androidx.compose.adaptive.navigation.suite)
    implementation(libs.androidx.appcompat)
    implementation(libs.gson)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val appId = "net.loeu.wallybudget"
val seedingTestClass = "net.loeu.wallybudget.seeding.EmulatorSeedInstrumentedTest"

fun runCommand(workingDir: File, command: List<String>): String {
    val process = ProcessBuilder(command)
        .directory(workingDir)
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) {
        "Command failed (${command.joinToString(" ")}):\n$output"
    }
    return output
}

tasks.register("seedDebugEmulator") {
    group = "verification"
    description = "Clears app data and seeds the debug app on a connected Android emulator."
    dependsOn("installDebug", "installDebugAndroidTest")

    doLast {
        val adb = androidComponents.sdkComponents.adb.get().asFile.absolutePath
        val devicesOutput = runCommand(project.projectDir, listOf(adb, "devices"))

        val emulatorSerials = devicesOutput
            .lineSequence()
            .drop(1)
            .map { it.trim() }
            .filter { it.endsWith("\tdevice") }
            .map { it.substringBefore('\t') }
            .filter { it.startsWith("emulator-") }
            .toList()

        check(emulatorSerials.isNotEmpty()) {
            "No running emulator detected. Start an Android emulator, then rerun ./gradlew seedDebugEmulator."
        }
        check(emulatorSerials.size == 1) {
            "Multiple emulators detected (${emulatorSerials.joinToString()}). Leave one emulator connected, then rerun ./gradlew seedDebugEmulator."
        }

        val serial = emulatorSerials.single()

        runCommand(project.projectDir, listOf(adb, "-s", serial, "shell", "pm", "clear", appId))
        runCommand(
            project.projectDir,
            listOf(
                adb,
                "-s",
                serial,
                "shell",
                "am",
                "instrument",
                "-w",
                "-e",
                "class",
                seedingTestClass,
                "$appId.test/androidx.test.runner.AndroidJUnitRunner"
            )
        )
    }
}

tasks.matching { task ->
    task.name.startsWith("connected") && task.name.endsWith("AndroidTest")
}.configureEach {
    doFirst {
        ensureOnlyEmulatorDevicesForConnectedTests()
    }
}
