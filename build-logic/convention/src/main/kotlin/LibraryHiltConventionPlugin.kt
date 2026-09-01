package com.xayah.buildlogic.convention
import org.gradle.api.Plugin
import org.gradle.api.Project
class LibraryHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply("com.google.dagger.hilt.android")
    }
}
