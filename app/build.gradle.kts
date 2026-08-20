plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.syncparty.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.syncparty.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:playback"))
    implementation(project(":core:bluetooth"))
    implementation(project(":core:networking"))
    implementation(project(":core:synchronization"))
    implementation(project(":core:audiotransfer"))
    implementation(project(":core:partyengine"))

    implementation(project(":feature:home"))
    implementation(project(":feature:createparty"))
    implementation(project(":feature:joinparty"))
    implementation(project(":feature:party"))
    implementation(project(":feature:medialibrary"))
    implementation(project(":feature:diagnostics"))

    implementation(project(":service:playback"))

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.navigation.compose)

    implementation(libs.zxing.android.embedded)
    implementation(libs.zxing.core)

    implementation(libs.kotlinx.coroutines.android)
}
