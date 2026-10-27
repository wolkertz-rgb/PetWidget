plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sting.petwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sting.petwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            // постоянный ключ подписи хранится в репозитории (keystore/)
            // чтобы обновления APK ставились поверх прежней версии
            val ksFile = file("../keystore/petdebug.keystore")
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("petDebug")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    signingConfigs {
        create("petDebug") {
            val ksFile = file("../keystore/petdebug.keystore")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = "petwidget123"
                keyAlias = "pet"
                keyPassword = "petwidget123"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
