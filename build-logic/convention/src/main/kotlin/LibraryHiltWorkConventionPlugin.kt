package com.xayah.buildlogic.convention
import org.gradle.api.Plugin
import org.gradle.api.Project
class LibraryHiltWorkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        plugins.apply("com.google.dagger.hilt.android")
    }
}
