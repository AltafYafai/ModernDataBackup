plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.library.common) apply false
    alias(libs.plugins.library.compose) apply false
    alias(libs.plugins.library.room) apply false
    alias(libs.plugins.library.hilt) apply false
    alias(libs.plugins.library.hilt.work) apply false
    alias(libs.plugins.application.common) apply false
    alias(libs.plugins.application.compose) apply false
    alias(libs.plugins.application.hilt) apply false
    alias(libs.plugins.application.hilt.work) apply false
}

subprojects {
    configurations.all {
        resolutionStrategy {
            eachDependency {
                if (requested.group == "org.bouncycastle") {
                    useVersion("1.78.1")
                }
            }
        }
    }
}
