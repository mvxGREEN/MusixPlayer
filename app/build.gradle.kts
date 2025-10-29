plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "green.mobileapps.offlinemusicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "green.mobileapps.offlinemusicplayer"
        minSdk = 24
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
    kotlinOptions {
        jvmTarget = "1.8"
    }
    viewBinding {
        enable = true
    }
}

dependencies {
    // AndroidX & UI
    implementation(libs.androidx.core.ktx.v1120)
    implementation(libs.androidx.appcompat.v161)
    implementation(libs.material.v1110)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    // Activity KTX for permission request contract
    implementation(libs.androidx.activity.ktx)

    // Coroutines for background threading
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // basic media3 modules
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")

    // dash playback
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")

    // hls playback
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")

    // session management
    implementation("androidx.media3:media3-session:1.8.0")

    // kotlin coroutines integration with ListenableFuture
    implementation("androidx.concurrent:concurrent-futures-ktx:1.3.0") // Or kotlin-coroutines-guava
}