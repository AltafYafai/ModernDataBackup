package com.xayah.buildlogic.convention
import org.gradle.api.*
class ApplicationHiltConventionPlugin : Plugin<Project> { override fun apply(t: Project) { t.plugins.apply("com.google.dagger.hilt.android") } }
