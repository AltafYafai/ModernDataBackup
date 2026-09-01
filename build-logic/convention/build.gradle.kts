plugins {
    `kotlin-dsl`
}
group = "com.xayah.buildlogic"

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.11.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.0.21")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.49")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.21-1.0.28")
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.0.21")
}

gradlePlugin {
    plugins {
        register("libraryCommon") { id = "library.common"; implementationClass = "com.xayah.buildlogic.convention.LibraryCommonConventionPlugin" }
        register("libraryCompose") { id = "library.compose"; implementationClass = "com.xayah.buildlogic.convention.LibraryComposeConventionPlugin" }
        register("libraryRoom") { id = "library.room"; implementationClass = "com.xayah.buildlogic.convention.LibraryRoomConventionPlugin" }
        register("libraryHilt") { id = "library.hilt"; implementationClass = "com.xayah.buildlogic.convention.LibraryHiltConventionPlugin" }
        register("libraryHiltWork") { id = "library.hilt.work"; implementationClass = "com.xayah.buildlogic.convention.LibraryHiltWorkConventionPlugin" }
        register("applicationCommon") { id = "application.common"; implementationClass = "com.xayah.buildlogic.convention.ApplicationCommonConventionPlugin" }
        register("applicationCompose") { id = "application.compose"; implementationClass = "com.xayah.buildlogic.convention.ApplicationComposeConventionPlugin" }
        register("applicationHilt") { id = "application.hilt"; implementationClass = "com.xayah.buildlogic.convention.ApplicationHiltConventionPlugin" }
        register("applicationHiltWork") { id = "application.hilt.work"; implementationClass = "com.xayah.buildlogic.convention.ApplicationHiltWorkConventionPlugin" }
    }
}
