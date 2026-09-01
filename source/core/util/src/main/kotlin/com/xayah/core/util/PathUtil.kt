package com.xayah.core.util

import android.content.Context
import android.os.Environment
import java.io.File

object PathUtil {
    const val BACKUP_DIR_NAME = "ModernDataBackup"

    fun getPrimaryBackupDir(context: Context): File {
        val external = Environment.getExternalStorageDirectory()
        val dir = if (external != null && external.canWrite()) {
            File(external, BACKUP_DIR_NAME)
        } else {
            File(context.getExternalFilesDir(null) ?: context.filesDir, BACKUP_DIR_NAME)
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
