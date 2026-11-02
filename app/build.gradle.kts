plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "uz.anketayordamchi"
    compileSdk = 34

    defaultConfig {
        // Eski "uz.censusassistant" dan butunlay boshqa ID: ikkalasi yonma-yon o'rnatiladi, hech biri yangilanmaydi.
        applicationId = "uz.anketayordamchi.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"
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
