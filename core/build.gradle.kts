plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id ("kotlin-kapt")
}

android {
    namespace = "com.core"
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
        dataBinding = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "PREFERENCE", "\"PREFERENCE\"")
        }
        release {

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "PREFERENCE", "\"PREFERENCE\"")
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
    //Dimension Dependency Added.
    api (libs.sdp.android)
    api (libs.ssp.android)
    api (libs.androidx.lifecycle.viewmodel.ktx)
    //lifecycle
    api (libs.androidx.lifecycle.extensions)
    api(libs.lifecycle.livedata.ktx)

    // ViewPager2
    api(libs.androidx.viewpager2)
    api(libs.androidx.fragment.ktx)
}