plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Versionsnummer zentral pflegen: bei jedem Release erhoehen.
val appVersionCode = 1
val appVersionName = "1.0.0"

android {
    namespace = "de.englischlernen.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.englischlernen.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "GITHUB_REPO", "\"Schnielz87/englisch-lern-appapk\"")
    }

    signingConfigs {
        create("release") {
            // Signatur-Daten kommen aus Umgebungsvariablen (GitHub-Secrets).
            // Fallback: oeffentlicher Schluessel im Repo, damit Builds immer installierbar
            // und untereinander update-faehig sind. Siehe README -> "Signatur".
            val ks = System.getenv("SIGNING_KEYSTORE_FILE")
            if (!ks.isNullOrBlank() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("signing/fallback-release.jks")
                storePassword = "englischlernen"
                keyAlias = "englischlernen"
                keyPassword = "englischlernen"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("robolectric.graphicsMode", "NATIVE")
                test.systemProperty("roborazzi.test.record", "true")
                test.systemProperty("roborazzi.record.filePathStrategy", "relativePathFromCurrentDirectory")
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
