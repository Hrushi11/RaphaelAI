import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// sherpa-onnx (on-device Silero VAD). The 50 MB AAR isn't committed; it's
// downloaded on first build and checked against a known SHA-256.
val sherpaVersion = "1.13.8"
val sherpaSha256 = "633c24321e06b1fe79feafa03ea16cbc0f8a286641e2da3559bac91bdb13bd96"
val sherpaAar = file("libs/sherpa-onnx-$sherpaVersion.aar")
val fetchSherpa by tasks.registering {
    outputs.file(sherpaAar)
    onlyIf { !sherpaAar.exists() }
    doLast {
        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
        val tmp = File(sherpaAar.parentFile.apply { mkdirs() }, "${sherpaAar.name}.part")
        uri(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        val digest = MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == sherpaSha256) { "sherpa-onnx AAR checksum mismatch: $digest" }
        tmp.renameTo(sherpaAar)
    }
}
tasks.named("preBuild") { dependsOn(fetchSherpa) }

// Release signing key: app-android/keystore.properties (git-ignored) points to a keystore
// outside the repo. Keep both backed up: updates must be signed with the same key.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "ai.raphael"
    compileSdk = 37

    defaultConfig {
        applicationId = "ai.raphael"
        minSdk = 31
        targetSdk = 35
        versionCode = 6
        versionName = "0.5.1"
        ndk {
            abiFilters += "arm64-v8a" // Pixel 9 and all recent phones; keeps the APK small
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // Shrink code + resources: drops ~40 MB of unused icon classes. sherpa-onnx JNI classes are kept in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(files("libs/sherpa-onnx-$sherpaVersion.aar"))
}
