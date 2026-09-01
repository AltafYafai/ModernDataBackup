package com.xayah.buildlogic.convention
import org.gradle.api.*
class ApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(t: Project) { with(t) {
        plugins.apply("com.android.application")
        plugins.apply("org.jetbrains.kotlin.android")
        plugins.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
            compileSdk = 35
            defaultConfig { minSdk = 24; targetSdk = 35 }
            buildFeatures { compose = true }
            compileOptions { sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17; targetCompatibility = org.gradle.api.JavaVersion.VERSION_17 }
        }
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { kotlinOptions { jvmTarget = "17" } }
    }}
}
