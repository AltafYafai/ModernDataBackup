plugins { alias(libs.plugins.library.room) }
dependencies { implementation(libs.androidx.room.runtime); implementation(libs.androidx.room.ktx); implementation(libs.kotlinx.coroutines.core); implementation(libs.hilt.android); implementation(project(":core:model")) }
