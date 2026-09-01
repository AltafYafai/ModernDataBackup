plugins { alias(libs.plugins.library.common) }
dependencies { implementation(project(":core:ui")); implementation(project(":core:model")); implementation(project(":core:data")); implementation(project(":core:datastore")); implementation(project(":core:work")); implementation(libs.libsu.core); implementation(libs.kotlinx.coroutines.core); implementation(libs.androidx.core.ktx) }
