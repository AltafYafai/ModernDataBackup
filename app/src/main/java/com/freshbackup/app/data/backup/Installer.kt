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

    /**
     * Silent install via root. Verifies the result and falls back to the
     * package-installer session API for split APKs. Returns true on success.
     */
    suspend fun installBatchRoot(apks: List<File>, pkg: String, log: (String) -> Unit): Boolean {
        if (!RootShell.isRooted()) {
            log("root required for batch install")
            return false
        }
        val tmp = "/data/local/tmp/fresh_$pkg"
        RootShell.exec("rm -rf $tmp && mkdir -p $tmp && chmod 777 $tmp")
        apks.forEach { apk ->
            RootShell.exec("cp -f '${apk.absolutePath}' '$tmp/${apk.name}' && chmod 644 '$tmp/${apk.name}'")
        }
        var ok = false
        if (apks.size == 1) {
            val r = RootShell.exec("pm install -r -d -g '$tmp/${apks.first().name}'")
            log(r.out.joinToString("\n").ifBlank { "(no installer output)" })
            ok = r.out.any { it.contains("Success", ignoreCase = true) }
        } else {
            val list = apks.joinToString(" ") { "'$tmp/${it.name}'" }
            val r = RootShell.exec("pm install-multiple -r -d -g $list")
            log(r.out.joinToString("\n").ifBlank { "(no installer output)" })
            ok = r.out.any { it.contains("Success", ignoreCase = true) }
            if (!ok) {
                log("install-multiple failed, trying installer session…")
                val d = "$"
                val r2 = RootShell.exec(
                    "SID=${d}(pm install-create -r -d -g 2>/dev/null | grep -o '[0-9]*' | tail -n1) ; " +
                        "if [ -n \"${d}SID\" ]; then for f in $tmp/*.apk; do SZ=${d}(stat -c%s \"${d}f\") ; " +
                        "BN=${d}(basename \"${d}f\") ; pm install-write -S ${d}SZ ${d}SID \"${d}BN\" \"${d}f\" ; done ; " +
                        "pm install-commit ${d}SID ; fi"
                )
                log(r2.out.joinToString("\n").ifBlank { "(no session output)" })
                ok = r2.out.any { it.contains("Success", ignoreCase = true) }
            }
        }
        RootShell.exec("rm -rf $tmp")
        if (!ok) {
            // Last word: does the package resolve now?
            val v = RootShell.exec("pm path $pkg")
            ok = v.isSuccess && v.out.any { it.contains("package:") }
            log(if (ok) "verified installed via pm path" else "install FAILED for $pkg")
        }
        return ok
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
