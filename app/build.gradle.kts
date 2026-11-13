plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tclstreamer.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tclstreamer.launcher"
        minSdk = 26
        targetSdk = 35
        // Each CI build counts up, so the app can tell when a newer one has been published.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1
        versionName = "1.$versionCode"
    }

    val sideloadKey = file("dnd.keystore")
    signingConfigs {
        // CI writes this from the SIGNING_KEY secret. Signing every build with the same key lets
        // a new build install over the old one, which self-updates need.
        if (sideloadKey.exists()) {
            create("sideload") {
                storeFile = sideloadKey
                storePassword = "dndlauncher"
                keyAlias = "dnd"
                keyPassword = "dndlauncher"
            }
        }
    }

    buildTypes {
        release {
            // Shrinking keeps the APK and its memory footprint small on a 1 GB TV.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without the shared key it falls back to the debug key, which still installs over ADB.
            signingConfig = signingConfigs.findByName("sideload") ?: signingConfigs.getByName("debug")
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
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
