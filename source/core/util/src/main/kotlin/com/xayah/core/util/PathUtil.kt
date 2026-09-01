package com.xayah.core.util

import android.content.Context
import android.os.Environment
import java.io.File

object PathUtil {
    const val BACKUP_DIR_NAME = "moderndatabackup"
    const val DEFAULT_BACKUP_PATH = "/sdcard/moderndatabackup"

    fun getPrimaryBackupDir(context: Context): File {
        val sdcard = File(DEFAULT_BACKUP_PATH)
        if (sdcard.exists() || sdcard.mkdirs() || sdcard.canWrite()) {
            return sdcard
        }
        val external = Environment.getExternalStorageDirectory()
        val dir = if (external != null) {
            File(external, BACKUP_DIR_NAME)
        } else {
            File(context.getExternalFilesDir(null) ?: context.filesDir, BACKUP_DIR_NAME)
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
