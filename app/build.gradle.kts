plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.todorpg.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.todorpg.app"
        minSdk = 26
        targetSdk = 35
        // CI passes its run number so every build installs as an upgrade.
        val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "0.3.$build"
    }

    // Sign with one fixed key (from the CI secrets) so each new APK installs over
    // the old one and keeps your save. Without the secrets, the standard debug
    // key is used, which only upgrades builds made on the same machine.
    val keystore = file("todorpg.keystore")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    val sideload = if (keystore.exists() && signingPassword != null) {
        signingConfigs.create("sideload") {
            storeFile = keystore
            storePassword = signingPassword
            keyAlias = "todorpg"
            keyPassword = signingPassword
        }
    } else null

    buildTypes {
        debug {
            if (sideload != null) signingConfig = sideload
        }
        release {
            if (sideload != null) signingConfig = sideload
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
