plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tathkirati.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tathkirati.app"
        minSdk = 26
        targetSdk = 33
        versionCode = 3
        versionName = "2.1"
    }

    signingConfigs {
        create("release") {
            // نفس المفتاح الرسمي — محفوظ بـ GitHub Secrets (SIGNING_KEY + SIGNING_PASSWORD)
            storeFile = file(System.getenv("SIGNING_KEY_FILE") ?: "/nonexistent")
            storeType = "pkcs12"
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = "p2p37mx"
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // مكتبة Android for Cars — هي الي تخلي التطبيق يطلع بقائمة أندرويد أوتو
    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.core:core-ktx:1.12.0")
}
