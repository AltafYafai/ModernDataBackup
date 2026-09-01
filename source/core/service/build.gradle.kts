plugins { alias(libs.plugins.library.common) }
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.zip4j)
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:work"))
    // libsu stubbed: implementation(libs.libsu.core)
}
