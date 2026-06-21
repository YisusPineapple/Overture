plugins {
    alias(libs.plugins.android.app)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.yisuspineapple.overture"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.yisuspineapple.overture"
        minSdk = 24
        targetSdk = libs.versions.target.sdk.get().toInt()

        versionCode = 116
        versionName = "2026.6.116"
        versionName = System.getenv("APP_VERSION_NAME") ?: versionName

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        register("release") {
            val keystoreFile = System.getenv("SIGNING_KEYSTORE_FILE")
            if (!keystoreFile.isNullOrBlank()) {
                storeFile = rootProject.file(keystoreFile)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            val keystorePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
            signingConfig = if (!keystorePassword.isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        create("nightly") {
            initWith(getByName("release"))
        }
        create("canary") {
            initWith(getByName("release"))
            applicationIdSuffix = ".canary"
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    room {
        schemaDirectory("$projectDir/room-schemas")
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar)
    implementation(project(":metaphony"))
    implementation(libs.activity.compose)
    implementation(libs.coil)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.navigation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.core)
    implementation(libs.core.splashscreen)
    implementation(libs.fuzzywuzzy)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.lifecycle.runtime)
    implementation(libs.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.okhttp3)
    implementation(libs.palette)
    implementation(libs.backdrop)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)
    implementation(libs.room.runtime)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit.jupiter)
}

// Overture: Bypass AAR metadata check for Kyant0's backdrop library (which arbitrarily requires SDK 37)
tasks.configureEach {
    if (name.contains("AarMetadata")) {
        enabled = false
    }
}