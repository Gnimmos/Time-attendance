// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            kotlinOptions.jvmTarget = "17"
        }
    }
}

allprojects {
    // ensure every Kotlin compile uses jvmTarget 17
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions.jvmTarget = "17"
    }
    tasks.withType<org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask>().configureEach {
        kotlinOptions.jvmTarget = "17"
    }

    // FORCE Kotlin stdlib 1.8.21 everywhere
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:1.8.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.21")
        }
    }
}
