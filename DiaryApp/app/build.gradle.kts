
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.supermenpark.dayflowdiary"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.supermenpark.dayflowdiary"
        minSdk = 23
        targetSdk = 35

        // 기존 버전 1에서 2로 증가
        versionCode = 2
        versionName = "0.1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
