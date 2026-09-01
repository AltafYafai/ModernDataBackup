plugins { alias(libs.plugins.library.common) }
android {
    namespace = "com.xayah.modernnative"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
}
dependencies { implementation(libs.kotlinx.coroutines.core) }
