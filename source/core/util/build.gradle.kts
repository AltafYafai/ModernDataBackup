plugins { alias(libs.plugins.library.common) }
dependencies { implementation(libs.kotlinx.coroutines.core); implementation(libs.androidx.core.ktx); implementation(project(":core:model")) }
