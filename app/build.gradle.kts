import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.yuubiz.yuubin"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.yuubiz.yuubin"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    // The release key stays outside the repository; the git-ignored keystore.properties says where it is. Without the file the
    // release variant still configures, so tests, lint and debug builds work anywhere, but packaging a release APK fails (below).
    val releaseKeystore = rootProject.file("keystore.properties")
    signingConfigs {
        if (releaseKeystore.isFile) {
            val props = Properties().apply { releaseKeystore.inputStream().use(::load) }
            val missing = listOf("storeFile", "storePassword", "keyAlias", "keyPassword").filter { props.getProperty(it).isNullOrBlank() }
            if (missing.isNotEmpty()) throw GradleException("keystore.properties lacks ${missing.joinToString()}; see keystore.properties.example.")
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // minSdk 28 verifies v2 and v3, so a v1 (JAR) signature would only add size; v4 would add an .idsig file nobody uses.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = false
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to the signed release on the same device.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Yuubin (debug)"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "2g"
                // Robolectric 4.17 on JDK 17+ needs access to FileDescriptor internals.
                test.jvmArgs(
                    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                )
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

// An unsigned or debug-signed release APK must never come out of this build: stop before packaging when the key is not set up.
tasks.named { it == "packageRelease" }.configureEach {
    val keystore = rootProject.file("keystore.properties")
    doFirst {
        if (!keystore.isFile) throw GradleException("Release signing is not set up: create keystore.properties in the repository root (see keystore.properties.example).")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
}
