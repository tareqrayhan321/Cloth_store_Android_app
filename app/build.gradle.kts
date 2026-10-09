plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.imran.clothstore"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clothingstore.myandroidapp"
        minSdk = 24
        targetSdk = 34
        versionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // HijrahDate (java.time.chrono, API 26+) মিনSDK ২৪-এও ব্যবহারযোগ্য করতে —
        // ক্যালেন্ডার স্ট্রিপের হিজরি তারিখের জন্য দরকার (দেখুন util/HijriCalendar.kt)
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // ── Core / Compose ──
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")

    // ── Navigation ──
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── ViewModel for Compose ──
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // ── Firebase (BoM দিয়ে ভার্সন সিঙ্ক করা) ──
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    // anonymous sign-in — Firestore rules-এ request.auth != null চেক পাস করাতে (আইটেম: auth)
    implementation("com.google.firebase:firebase-auth")

    // ── Coroutines with Firestore await() ──
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // ── Debug tooling ──
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Core library desugaring — java.time.chrono.HijrahDate কে minSdk 24-এ সমর্থন দিতে ──
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // ── DataStore — নোটিফিকেশন লিস্ট প্রসেস-কিলের পরও টিকিয়ে রাখতে ──
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Coil — base64-এ Firestore-এ রাখা পার্টি প্রোফাইল ছবি প্রদর্শনের জন্য (আইটেম #১১) ──
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ── Room — অফলাইন-ফার্স্ট লোকাল ক্যাশ (আইটেম #১) ──
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // ── WorkManager — ব্যাকগ্রাউন্ড Firestore sync (আইটেম #১) ──
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // ── kotlinx.serialization — BackupPayload-কে Room ক্যাশে JSON হিসেবে (de)serialize করতে ──
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // ── ইউনিট টেস্ট — SyncEngine merge/conflict-resolution লজিকের জন্য (নিয়ম ৫.৭) ──
    testImplementation("junit:junit:4.13.2")
}
