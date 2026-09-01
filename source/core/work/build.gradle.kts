plugins { alias(libs.plugins.library.common) }
dependencies { implementation(libs.androidx.work.runtime.ktx); implementation(libs.kotlinx.coroutines.core); implementation(project(":core:model")); implementation(project(":core:data")); implementation(project(":core:datastore")) }
