package com.freshbackup.app.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.freshbackup.app.core.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * APK install + share. Single APKs install without root via the system
 * installer; batch installs use root pm. Shizuku batch is a future step.
 */
@Singleton
class Installer @Inject constructor(@ApplicationContext private val context: Context) {

    /** System installer UI for one APK (no root needed). */
    suspend fun installManual(apk: File) = withContext(Dispatchers.Main) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** Silent batch install via root. Returns output for logging. */
    suspend fun installBatchRoot(apks: List<File>, pkg: String): String {
        if (!RootShell.isRooted()) return "root required for batch install"
        val tmp = "/data/local/tmp/fresh_$pkg"
        RootShell.exec("rm -rf $tmp && mkdir -p $tmp && chmod 777 $tmp")
        apks.forEach { apk ->
            RootShell.exec("cp -f '${apk.absolutePath}' '$tmp/${apk.name}' && chmod 644 '$tmp/${apk.name}'")
        }
        val out = if (apks.size == 1) {
            RootShell.exec("pm install -r -d -g '$tmp/${apks.first().name}'")
        } else {
            val list = apks.joinToString(" ") { "'$tmp/${it.name}'" }
            RootShell.exec("pm install-multiple -r -d -g $list")
        }
        RootShell.exec("rm -rf $tmp")
        return out.out.joinToString("\n")
    }

    /** ACTION_SEND intent for sharing an APK with other apps. */
    fun shareIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
