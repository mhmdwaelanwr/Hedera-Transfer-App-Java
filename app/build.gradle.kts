plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "anwar.mlsa.hadera.aou"
    compileSdk = 36

    defaultConfig {
        applicationId = "anwar.mlsa.hadera.aou"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // UI
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.android.volley:volley:1.2.1")

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Hedera
    implementation("com.hedera.hashgraph:sdk:2.26.0")

    // Other dependencies
    implementation("com.github.yuriy-budiyev:code-scanner:2.3.2")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.code.gson:gson:2.13.2")
    
    // WorkManager
    implementation("androidx.work:work-runtime:2.9.0")
    
    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")
    
    // Security
    implementation("androidx.security:security-crypto:1.1.0")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Guava
    implementation("com.google.guava:guava:33.0.0-android")

    // USB Serial
    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
}