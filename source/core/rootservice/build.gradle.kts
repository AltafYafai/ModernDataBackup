plugins { alias(libs.plugins.library.common) }
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:model"))
    // libsu temporarily stubbed - re-add when JitPack version resolved
    // implementation(libs.libsu.core); implementation(libs.libsu.service)
}
