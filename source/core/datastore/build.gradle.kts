plugins { alias(libs.plugins.library.common) }
dependencies { implementation(libs.androidx.datastore.preferences); implementation(libs.kotlinx.coroutines.core); implementation(project(":core:model")) }
