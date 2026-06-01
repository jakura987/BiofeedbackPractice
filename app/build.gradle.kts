plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}


android {
    namespace = "com.intellizondy.biofeedback"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.intellizondy.biofeedback"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    implementation("com.google.dagger:hilt-android:2.56.2")
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.activity)

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    // Gson 库（如果你只用 converter-gson，有时也可以不显式加）
    implementation("com.google.code.gson:gson:2.10.1")
    // Retrofit 的 Gson 转换器
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    

    implementation ("com.squareup.retrofit2:adapter-rxjava2:2.9.0")
    implementation(libs.androidx.databinding.runtime)


    kapt("com.google.dagger:hilt-compiler:2.56.2")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("com.jakewharton.timber:timber:5.0.1")

    // RxJava2 核心库
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    // RxAndroid：提供 Android main-thread 调度器等
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")

    //wavefrom demo下载的依赖
    implementation(libs.mpandroidchart)
    implementation(libs.rxrelay)
    implementation(libs.autodispose.android)
    implementation(libs.autodispose.android.archcomponents)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)



}

