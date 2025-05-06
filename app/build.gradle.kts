plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android) version "1.8.21" // Ensure it's a stable version
    id("com.google.devtools.ksp") version "1.8.21-1.0.11"
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.myapplication"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }


    sourceSets {
        named("main") {
            jniLibs.srcDirs("src/main/jniLibs")
            resources.srcDir("libs")
        }
    }

}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("com.android.volley:volley:1.2.1")
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.room.runtime.android)
    implementation(libs.play.services.basement)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(project(":opencv"))
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation("androidx.camera:camera-core:1.2.1")
    implementation("androidx.camera:camera-camera2:1.2.1")
    implementation("androidx.camera:camera-lifecycle:1.2.1")
    implementation("androidx.camera:camera-view:1.2.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okio:okio:3.6.0")
    implementation("androidx.room:room-runtime:2.5.1")
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    ksp("androidx.room:room-compiler:2.5.1")
}