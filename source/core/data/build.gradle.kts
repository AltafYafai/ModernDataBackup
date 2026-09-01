plugins { alias(libs.plugins.library.common) }
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.hilt.android)
    implementation(libs.libsu.core)
    implementation(libs.zip4j)
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(libs.androidx.datastore.preferences)
    implementation(project(":core:network"))
    implementation(project(":core:rootservice"))
}
