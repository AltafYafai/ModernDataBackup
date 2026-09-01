plugins {
    alias(libs.plugins.library.room)
    alias(libs.plugins.library.hilt)
    alias(libs.plugins.ksp)
}
dependencies {
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(project(":core:model"))
}
