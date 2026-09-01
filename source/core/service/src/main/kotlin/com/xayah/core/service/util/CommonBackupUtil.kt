package com.xayah.core.service.util
class CommonBackupUtil {
    suspend fun backupApp(packageName: String, dest: String): Result<Unit> = Result.success(Unit)
    suspend fun restoreApp(packageName: String, src: String): Result<Unit> = Result.success(Unit)
}
