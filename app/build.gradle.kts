plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Один источник номера для установщика Android и отображаемой версии.
// Локальная сборка помечается отдельно, чтобы не путать её с APK из Actions.
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.takeIf { it > 0 }

android {
    namespace = "com.sting.petwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sting.petwidget"
        minSdk = 26
        targetSdk = 35
        versionCode = ciBuildNumber ?: 1
        versionName = ciBuildNumber?.let { "1.$it" } ?: "1.0-local"
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

    buildTypes {
        debug {
            // постоянный ключ подписи хранится в репозитории (keystore/)
            // чтобы обновления APK ставились поверх прежней версии
            val ksFile = file("../keystore/petdebug.keystore")
            if (!ksFile.exists() && System.getenv("CI") != null) {
                error("keystore/petdebug.keystore отсутствует в репозитории — CI не может собрать")
            }
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("petDebug")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // только тестовая зависимость (в APK не попадает)
    testImplementation("junit:junit:4.13.2")
}
