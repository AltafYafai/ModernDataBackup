plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.xayah.modernnative"; compileSdk = 35
    defaultConfig { minSdk = 24 }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
}
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0") }
