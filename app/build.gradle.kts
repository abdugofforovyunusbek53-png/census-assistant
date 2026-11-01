plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uz.censusassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "uz.censusassistant.new"
        minSdk = 26
        targetSdk = 33
        versionCode = 5
        versionName = "0.5-avto"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
