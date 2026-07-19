plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.staacks.alpharemote"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.staacks.alpharemote"
        minSdk = 36
        targetSdk = 37
        versionCode = 1000
        versionName = "1.00"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
        // Disable unused AGP features
        aidl = false
        shaders = false
    }

    dependenciesInfo {
        includeInApk = false
    }
}

dependencies {

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.materialWindow)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.savedstate.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.window)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)
    implementation(libs.accompanist.permissions)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)


}