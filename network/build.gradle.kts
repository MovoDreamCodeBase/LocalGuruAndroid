import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.network"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"https://studentmanagement20251120231138-f2d3fbcuguf0a4gy.eastasia-01.azurewebsites.net/api/\"")
        }
        release {

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://studentmanagement20251120231138-f2d3fbcuguf0a4gy.eastasia-01.azurewebsites.net/api/\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    api (libs.androidx.lifecycle.viewmodel.ktx)

    //lifecycle
    api (libs.androidx.lifecycle.extensions)

    // Retrofit
    api (libs.retrofit)
    api (libs.converter.gson)

    api (libs.logging.interceptor)
    api (libs.okhttp3.okhttp.urlconnection)

    api (libs.gson)

    implementation(project(":core"))
    implementation(project(":data"))

}