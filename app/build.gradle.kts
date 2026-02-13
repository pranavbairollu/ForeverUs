import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

// Read the local.properties file
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.example.foreverus"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.foreverus"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Securely add the API key to BuildConfig
        val geminiApiKey = (localProperties.getProperty("gemini.api.key") ?: "").removeSurrounding("\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        val youtubeApiKey = (localProperties.getProperty("youtube.api.key") ?: "").removeSurrounding("\"")
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$youtubeApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true // Enable BuildConfig generation
    }
    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/INDEX.LIST")
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
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Firebase (use latest BOM for gRPC stability)
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-functions")

    // Room
    implementation(libs.room.runtime)
    kapt(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.guava)

    // Glide
    implementation(libs.glide)
    kapt(libs.glide.compiler)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation("androidx.lifecycle:lifecycle-reactivestreams:2.6.2")

    // Navigation
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    // Google AI
    implementation(libs.generative.ai)

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0-alpha01")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // YouTube Player (safe)
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:11.1.0")

    // Shimmer
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Compressor
    implementation("id.zelory:compressor:3.0.1")

    // YouTube Data API (⚠ pulls old gRPC → force latest gRPC)
    implementation("com.google.apis:google-api-services-youtube:v3-rev20240417-2.0.0")
    implementation("com.google.api-client:google-api-client-android:2.4.0")
    implementation("com.google.http-client:google-http-client-android:1.43.3")
    implementation("com.google.oauth-client:google-oauth-client:1.36.0")

    // Cloudinary
    implementation("com.cloudinary:cloudinary-android:3.0.2")

    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")
    implementation("androidx.media3:media3-common:1.2.0")

    // Konfetti
    implementation("nl.dionsegijn:konfetti-xml:2.0.4")
    
    // Palette API for dynamic colors
    implementation("androidx.palette:palette:1.0.0")

    // EXIF Interface for Smart Date
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
