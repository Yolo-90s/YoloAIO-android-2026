import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Pull release signing creds from local.properties so they never end up in
// version control. If the file or any key is missing, release builds fall
// back to unsigned — the build still works for `assembleDebug`.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseKeystorePath: String? = localProps.getProperty("YOLO_KEYSTORE_FILE")
val releaseKeystorePassword: String? = localProps.getProperty("YOLO_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = localProps.getProperty("YOLO_KEY_ALIAS")
val releaseKeyPassword: String? = localProps.getProperty("YOLO_KEY_PASSWORD")
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() &&
    !releaseKeystorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.example.yoloaio"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.yoloaio"
        minSdk = 24
        targetSdk = 36
        versionCode = 27
        versionName = "1.10.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // arm64-v8a only. Drops native libs for:
        //  - x86 / x86_64 (emulator + ChromeOS Arc++ only)
        //  - armeabi-v7a (32-bit phones, <5% of Androids in service, none
        //                 in our 5-10 tester pool)
        // Reduces APK from ~150 MB universal → ~50 MB. If a tester ever
        // shows up on a 32-bit-only phone they'll get "App not installed"
        // — add "armeabi-v7a" back here in that case.
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    buildFeatures {
        compose = true
        // Enable generation of the BuildConfig class so MainActivity can
        // gate WebView debugging on BuildConfig.DEBUG.
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    // App Distribution in-app update SDK. NOT part of the Firebase BoM — it
    // ships its own beta-labelled but production-stable versioning. Powers
    // the "Update available" prompt for testers when a newer APK is pushed
    // to App Distribution. Bundle only in release builds intended for App
    // Distribution; for a future Play Store flavour, swap to
    // `firebase-appdistribution-api` (no-op stub).
    implementation("com.google.firebase:firebase-appdistribution:16.0.0-beta14")

    // Jitsi Meet Android SDK — native (React Native under the hood) replacement
    // for the WebView call surface that couldn't enumerate the camera on
    // some OEM WebViews (Vivo / Android 16). Adds ~30 MB to the APK, but
    // is the only path Jitsi officially supports for Android calling.
    // Exposes JitsiMeetActivity which we launch via Intent.
    implementation("org.jitsi.react:jitsi-meet-sdk:10.2.0")
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.coil.compose)
    implementation(libs.androidx.work.runtime.ktx)

    // Google Sign-In via Credential Manager + Google Identity Services.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    // MediaSessionCompat + MediaStyle notification for the music foreground
    // playback service. Powers lockscreen + Bluetooth headset transport.
    implementation(libs.androidx.media)

    // Google Cast / Chromecast — used for Music's "Cast" button. Also pulls
    // in MediaRouter transitively, but we declare it explicitly so we can
    // host MediaRouteButton in Compose via AndroidView.
    implementation(libs.androidx.mediarouter)
    implementation(libs.google.cast.framework)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
