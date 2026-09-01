plugins {
    alias(libs.plugins.library.common)
    alias(libs.plugins.library.hilt)
    alias(libs.plugins.ksp)
}
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.libsu.core)
    implementation(libs.zip4j)
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.datastore.preferences)
    implementation(project(":core:network"))
    implementation(project(":core:rootservice"))
    implementation(project(":core:util"))
}
