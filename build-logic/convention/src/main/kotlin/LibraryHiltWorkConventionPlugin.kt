package com.xayah.buildlogic.convention
import org.gradle.api.*
class LibraryHiltWorkConventionPlugin : Plugin<Project> { override fun apply(t: Project) { with(t) { plugins.add("com.google.dagger.hilt.android"); plugins.add("com.google.dagger.hilt.android.compiler") } } }
