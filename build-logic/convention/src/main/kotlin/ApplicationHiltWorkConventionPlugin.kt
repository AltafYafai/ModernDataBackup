package com.xayah.buildlogic.convention
import org.gradle.api.*
class ApplicationHiltWorkConventionPlugin : Plugin<Project> {
    override fun apply(t: Project) { with(t) {
        plugins.apply("com.google.dagger.hilt.android")
    }}
}
