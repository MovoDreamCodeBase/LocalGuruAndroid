plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    id("kotlin-parcelize")
}

android {
    namespace = "com.movodream.localguru"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.movodream.localguru"
        minSdk = 24
        targetSdk = 36
        versionCode = 122
        versionName = "1.2.2"

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 🔹 Used by CI to read versionName
    tasks.register("printVersionName") {
        doLast {
            println(android.defaultConfig.versionName)
        }
    }
    flavorDimensions += "environment"
    productFlavors {

        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"

        }

        create("prod") {
            dimension = "environment"

        }
    }

    // 🔹 SIGNING CONFIGS (THIS IS THE KEY PART)
    signingConfigs {

        // ✅ QC / CI debug signing (shared)
        getByName("debug") {
            storeFile = file("ci-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // 🔐 Production signing (placeholder)
        create("release") {
            // These will come from CI secrets later
            // DO NOT hardcode real values
        }
    }

    buildTypes {

        // 🔹 QC build
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }

        // 🔹 Production build
        release {
            signingConfig = signingConfigs.getByName("release")
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
        dataBinding = true
        buildConfig = true
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.firebase.database)
    implementation (project(":core"))
    implementation(project(":data"))
    implementation(project(":network"))
    implementation("com.google.android.flexbox:flexbox:3.0.0")


}