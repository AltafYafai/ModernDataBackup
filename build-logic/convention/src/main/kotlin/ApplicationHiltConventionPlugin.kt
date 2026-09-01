package com.xayah.buildlogic.convention
import org.gradle.api.Plugin
import org.gradle.api.Project
class ApplicationHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply("com.google.dagger.hilt.android")
        target.plugins.apply("com.google.devtools.ksp")
    }
}
