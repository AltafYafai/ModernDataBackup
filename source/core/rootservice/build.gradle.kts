plugins { alias(libs.plugins.library.common) }
dependencies { implementation(libs.libsu.core); implementation(libs.libsu.service); implementation(libs.kotlinx.coroutines.core); implementation(project(":core:model")) }
