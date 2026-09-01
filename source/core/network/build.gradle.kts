plugins { alias(libs.plugins.library.common) }
dependencies { implementation(libs.okhttp); implementation(libs.retrofit); implementation(libs.kotlinx.coroutines.core); implementation(libs.smbj); implementation(libs.sshj); implementation(project(":core:model")) }
