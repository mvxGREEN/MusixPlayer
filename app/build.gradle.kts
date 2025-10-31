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

    // TODO figure out why 'media3' package cannot be found when declared from version catalog
    val media3_version = "1.8.0"

    // For media playback using ExoPlayer
    implementation("androidx.media3:media3-exoplayer:$media3_version")

    // For building media playback UIs using Views
    implementation("androidx.media3:media3-ui:$media3_version")

    // Common functionality for reading and writing media containers
    implementation("androidx.media3:media3-container:$media3_version")
    // Common functionality for media database components
    implementation("androidx.media3:media3-database:$media3_version")
    // Common functionality for media decoders
    implementation("androidx.media3:media3-decoder:$media3_version")
    // Common functionality for loading data
    implementation("androidx.media3:media3-datasource:$media3_version")
    // Common functionality used across multiple media libraries
    implementation("androidx.media3:media3-common:$media3_version")
    // Common Kotlin-specific functionality
    implementation("androidx.media3:media3-common-ktx:$media3_version")

    // For transforming media files
    implementation("androidx.media3:media3-transformer:$media3_version")

    // For exposing and controlling media sessions
    implementation("androidx.media3:media3-session:$media3_version")

    // For extracting data from media containers
    implementation("androidx.media3:media3-extractor:$media3_version")

    // For building media playback UIs for Android TV using the Jetpack Leanback library
    //implementation("androidx.media3:media3-ui-leanback:$media3_version")

    // For integrating with Cast
    //implementation("androidx.media3:media3-cast:$media3_version")

    // For scheduling background operations using Jetpack Work's WorkManager with ExoPlayer
    //implementation("androidx.media3:media3-exoplayer-workmanager:$media3_version")

    // For applying effects on video frames
    //implementation("androidx.media3:media3-effect:$media3_version")

    // For muxing media files
    //implementation("androidx.media3:media3-muxer:$media3_version")

    // Utilities for testing media components (including ExoPlayer components)
    //implementation("androidx.media3:media3-test-utils:$media3_version")
    // Utilities for testing media components (including ExoPlayer components) via Robolectric
    //implementation("androidx.media3:media3-test-utils-robolectric:$media3_version")
}