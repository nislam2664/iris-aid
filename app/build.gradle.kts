plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.irisaid"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.irisaid"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    var version = "1.5.2"
    implementation("androidx.camera:camera-core:${version}")
    implementation("androidx.camera:camera-camera2:${version}")
    implementation("androidx.camera:camera-lifecycle:${version}")
    implementation("androidx.camera:camera-view:${version}")
    implementation("com.google.mediapipe:tasks-vision:0.10.26")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation ("com.google.mlkit:face-detection:16.1.6")
}