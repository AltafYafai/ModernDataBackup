plugins { alias(libs.plugins.application.compose); alias(libs.plugins.application.hilt); alias(libs.plugins.application.hilt.work) }
android {
    namespace = "com.xayah.moderndatabackup"; compileSdk = 35
    defaultConfig { applicationId = "com.xayah.moderndatabackup"; minSdk = 24; targetSdk = 35; versionCode = 1000000; versionName = "1.0.0" }
    buildTypes { getByName("release") { isMinifyEnabled = false } }
}
dependencies {
    implementation(project(":core:common")); implementation(project(":core:ui")); implementation(project(":core:model"))
    implementation(project(":core:database")); implementation(project(":core:data")); implementation(project(":core:datastore"))
    implementation(project(":core:util")); implementation(project(":core:work")); implementation(project(":core:rootservice"))
    implementation(project(":core:network")); implementation(project(":core:service"))
    implementation(project(":feature:main:dashboard")); implementation(project(":feature:main:list"))
    implementation(project(":feature:main:backup")); implementation(project(":feature:main:restore"))
    implementation(project(":feature:main:settings")); implementation(project(":feature:main:processing"))
    implementation(project(":feature:main:cloud")); implementation(project(":feature:main:history"))
    implementation(project(":feature:main:details")); implementation(project(":feature:main:configuration"))
    implementation(project(":feature:setup")); implementation(project(":feature:crash"))
    implementation(libs.androidx.core.splashscreen); implementation(libs.libsu.core); implementation(libs.libsu.service)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
