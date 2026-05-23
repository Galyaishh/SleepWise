plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.sleepwisepoc.wear"
    compileSdk = 35

    defaultConfig {
        // Must match the phone app so Wearable Data Layer pairs them up.
        applicationId = "com.example.sleepwisepoc"
        minSdk = 30           // Wear OS 3.0 baseline (Galaxy Watch 4+)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Wear-OS UI baseline (we keep the UI minimal — just an "active" screen)
    implementation("androidx.wear:wear:1.3.0")
    implementation("com.google.android.support:wearable:2.9.0")
    compileOnly("com.google.android.wearable:wearable:2.9.0")

    // Health Services — real-time HR sensor stream (no Samsung partner approval)
    implementation("androidx.health:health-services-client:1.0.0-rc01")
    implementation("com.google.guava:guava:33.0.0-android") // ListenableFuture for HS

    // Wearable Data Layer API — push samples from watch → phone over BT
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Coroutines for suspend-style callback adaptation
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
