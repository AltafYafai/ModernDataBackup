plugins { alias(libs.plugins.library.common) }
dependencies { implementation(libs.kotlinx.serialization.core); implementation(libs.kotlinx.serialization.protobuf); implementation(libs.androidx.core.ktx) }
