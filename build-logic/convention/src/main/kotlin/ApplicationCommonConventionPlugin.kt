package com.xayah.buildlogic.convention
import org.gradle.api.Plugin
import org.gradle.api.Project
class ApplicationCommonConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        plugins.apply("com.android.application")
        plugins.apply("org.jetbrains.kotlin.android")
        plugins.apply("org.jetbrains.kotlin.plugin.serialization")
        extensions.configure(com.android.build.api.dsl.ApplicationExtension::class.java) {
            namespace = "com.xayah.moderndatabackup"
            compileSdk = 35
            defaultConfig {
                applicationId = "com.xayah.moderndatabackup"
                minSdk = 24
                targetSdk = 35
                versionCode = 1000000
                versionName = "1.0.0"
            }
            buildTypes.getByName("release") { isMinifyEnabled = false }
            compileOptions {
                sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
            }
            buildFeatures { compose = true }
        }
        tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
            kotlinOptions { jvmTarget = "17" }
        }
    }
}
