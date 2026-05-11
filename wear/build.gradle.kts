plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "rocks.talon.marrow.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "rocks.talon.marrow"
        minSdk = 33  // Wear OS 4+; we target Pixel Watch 3 (Wear OS 6).
        targetSdk = 36
        versionCode = 9
        versionName = "0.7.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/INDEX.LIST") }
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    // Wear Compose Material 3 — Expressive on watch.
    implementation("androidx.wear.compose:compose-material3:1.5.6")
    implementation("androidx.wear.compose:compose-foundation:1.5.6")
    implementation("androidx.wear.compose:compose-navigation:1.5.6")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.wear:wear-tooling-preview:1.0.0")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    // Wear OS Tile (at-a-glance live stats on the watch face).
    // Tiles pulls protolayout transitively; pinning explicit versions to
    // avoid surprise upgrades during the wider AndroidX rev cycle.
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.1")
    // Guava is required for `Futures.immediateFuture(...)` returned by the
    // tile / resources callbacks. Tiles itself only consumes the
    // `ListenableFuture` interface (provided by `concurrent-futures`), not
    // the helper factories — so we add `guava` explicitly. The
    // `-android` flavour avoids the JRE-only dependency on `j2objc-annotations`.
    implementation("com.google.guava:guava:33.4.0-android")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
