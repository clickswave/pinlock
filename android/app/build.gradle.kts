import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The release key comes from a gitignored keystore.properties next to this
// project, or from PINLOCK_* environment variables. Without one, release builds
// are signed with the debug key, which is fine for a demo you build yourself.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}

fun signingValue(prop: String, env: String): String? =
    (keystoreProps.getProperty(prop) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStore = signingValue("storeFile", "PINLOCK_KEYSTORE")
val releaseStorePassword = signingValue("storePassword", "PINLOCK_KEYSTORE_PASSWORD")
val releaseAlias = signingValue("keyAlias", "PINLOCK_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "PINLOCK_KEY_PASSWORD")
val hasReleaseKey = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword).all { it != null }

// The API host. Override with -Ppinlock.host=... to test against another host
// under clickswave.org; the network security config pins the whole domain.
val apiHost = (findProperty("pinlock.host") as String?) ?: "pinlock.clickswave.org"

android {
    namespace = "org.clickswave.pinlock"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.clickswave.pinlock"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "API_HOST", "\"$apiHost\"")
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Left unminified on purpose. This app is a teaching target, and its
            // pinning code should read the same in a decompiler as it does here.
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
