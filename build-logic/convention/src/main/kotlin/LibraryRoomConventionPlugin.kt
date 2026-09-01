package com.xayah.buildlogic.convention
import org.gradle.api.Plugin
import org.gradle.api.Project
class LibraryRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        plugins.apply("com.android.library")
        plugins.apply("org.jetbrains.kotlin.android")
        plugins.apply("com.google.devtools.ksp")
        val ns = "com.xayah." + path.removePrefix(":").replace(":", ".").replace("-", "")
        extensions.configure(com.android.build.api.dsl.LibraryExtension::class.java) {
            namespace = ns
            compileSdk = 35
            defaultConfig { minSdk = 24 }
            compileOptions {
                sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
            }
        }
        tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).configureEach {
            kotlinOptions { jvmTarget = "17" }
        }
        dependencies.add("implementation", "androidx.room:room-runtime:2.6.1")
        dependencies.add("implementation", "androidx.room:room-ktx:2.6.1")
        dependencies.add("ksp", "androidx.room:room-compiler:2.6.1")
    }
}
