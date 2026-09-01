package com.xayah.buildlogic.convention
import org.gradle.api.*
class LibraryHiltConventionPlugin : Plugin<Project> { override fun apply(t: Project) { t.plugins.add("com.google.dagger.hilt.android") } }
