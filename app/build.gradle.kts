import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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

android {
    namespace = "net.loeu.wallybudget"
    compileSdk = 36

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
            isMinifyEnabled = false
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
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
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
    implementation(libs.material)

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

tasks.matching { task ->
    task.name.startsWith("connected") && task.name.endsWith("AndroidTest")
}.configureEach {
    doFirst {
        ensureOnlyEmulatorDevicesForConnectedTests()
    }
}
