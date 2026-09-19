package com.freshbackup.app.data.backup

import com.freshbackup.app.core.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Private app-data backup/restore via root shell. Covers:
 * /data/user/0 (CE), /data/user_de/0 (DE), /sdcard/Android/{data,media,obb},
 * per-app SSAID identity and granted runtime permissions.
 */
@Singleton
class AppData @Inject constructor() {

    suspend fun backup(
        pkg: String,
        destDir: File,
        includeData: Boolean,
        includeDe: Boolean,
        includeExt: Boolean,
        includeMedia: Boolean,
        includeObb: Boolean,
        log: (String) -> Unit
    ): Map<String, Long> = withContext(Dispatchers.IO) {
        val sizes = mutableMapOf<String, Long>()
        destDir.mkdirs()
        val d = "$"

        if (includeData) {
            val dest = File(destDir, "data.tar.gz")
            dest.delete()
            val cmd = "TARGET=\"\" ; [ -d \"/data/user/0/$pkg\" ] && TARGET=\"/data/user/0/$pkg\" ; " +
                "[ -z \"${d}TARGET\" ] && [ -d \"/data/data/$pkg\" ] && TARGET=\"/data/data/$pkg\" ; " +
                "if [ -n \"${d}TARGET\" ]; then cd \"${d}TARGET\" ; " +
                "ITEMS=${d}(ls -A 2>/dev/null | grep -vE '^(lib|cache|code_cache)${d}') ; " +
                "if [ -n \"${d}ITEMS\" ]; then tar -czf '${dest.absolutePath}' ${d}ITEMS 2>/dev/null || tar -cf '${dest.absolutePath}' ${d}ITEMS 2>/dev/null ; " +
                "chmod 644 '${dest.absolutePath}' ; fi ; fi"
            RootShell.exec(cmd)
            if (dest.exists()) {
                sizes["data"] = dest.length()
                log("data: ${dest.length() / 1024} KB")
            }
        }
        if (includeDe) {
            val dest = File(destDir, "data_de.tar.gz")
            dest.delete()
            val cmd = "if [ -d \"/data/user_de/0/$pkg\" ]; then cd \"/data/user_de/0/$pkg\" ; " +
                "ITEMS=${d}(ls -A 2>/dev/null | grep -vE '^(lib|cache|code_cache)${d}') ; " +
                "if [ -n \"${d}ITEMS\" ]; then tar -czf '${dest.absolutePath}' ${d}ITEMS 2>/dev/null || tar -cf '${dest.absolutePath}' ${d}ITEMS 2>/dev/null ; " +
                "chmod 644 '${dest.absolutePath}' ; fi ; fi"
            RootShell.exec(cmd)
            if (dest.exists()) sizes["data_de"] = dest.length()
        }
        tarDir("/sdcard/Android/data/$pkg", File(destDir, "ext.tar.gz"), "ext", sizes, log)
        tarDir("/sdcard/Android/media/$pkg", File(destDir, "media.tar.gz"), "media", sizes, log)
        tarDir("/sdcard/Android/obb/$pkg", File(destDir, "obb.tar.gz"), "obb", sizes, log)

        // SSAID identity + granted permissions for faithful restore.
        if (includeExt || includeData) {
            val ssaid = RootShell.exec(
                "grep -B 1 '$pkg' /data/system/users/0/settings_ssaid.xml 2>/dev/null | grep 'value=' | sed 's/.*value=\"//;s/\".*//'"
            ).out.firstOrNull()?.trim()
            if (!ssaid.isNullOrEmpty()) {
                File(destDir, "ssaid.txt").writeText(ssaid)
            }
            val perms = RootShell.exec("dumpsys package $pkg 2>/dev/null | grep 'permission.*granted=true'")
            val granted = perms.out.mapNotNull { line ->
                val p = line.substringBefore(": granted=true").trim().substringAfterLast(" ")
                p.takeIf { it.startsWith("android.permission.") }
            }
            if (granted.isNotEmpty()) {
                File(destDir, "permissions.txt").writeText(granted.joinToString("\n"))
            }
        }
        sizes
    }

    suspend fun restore(
        pkg: String,
        srcDir: File,
        restoreData: Boolean,
        restoreDe: Boolean,
        restoreExt: Boolean,
        restoreMedia: Boolean,
        restoreObb: Boolean,
        restoreIdentity: Boolean,
        log: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        RootShell.forceStop(pkg)
        val uid = RootShell.appUid(pkg)

        if (restoreData) {
            val tar = File(srcDir, "data.tar.gz").takeIf { it.exists() }
            if (tar != null) {
                val d = "$"
                RootShell.exec(
                    "mkdir -p '/data/user/0/$pkg' '/data/data/$pkg' ; " +
                        "LIB=${d}(readlink /data/user/0/$pkg/lib || readlink /data/data/$pkg/lib) ; " +
                        "(cd '/data/user/0/$pkg' && tar -xzf '${tar.absolutePath}' 2>/dev/null) ; " +
                        "(cd '/data/data/$pkg' && tar -xzf '${tar.absolutePath}' 2>/dev/null) ; " +
                        "if [ -n \"${d}LIB\" ]; then ln -sfn \"${d}LIB\" '/data/user/0/$pkg/lib' ; ln -sfn \"${d}LIB\" '/data/data/$pkg/lib' ; fi"
                )
                if (uid != null) {
                    RootShell.exec(
                        "chown -R ${uid.first}:${uid.second} '/data/user/0/$pkg' '/data/data/$pkg' 2>/dev/null ; " +
                            "chmod 755 '/data/user/0/$pkg' '/data/data/$pkg' 2>/dev/null"
                    )
                }
                RootShell.exec("restorecon -FR '/data/user/0/$pkg' '/data/data/$pkg' 2>/dev/null")
                log("data restored")
            }
        }
        if (restoreDe) {
            val tar = File(srcDir, "data_de.tar.gz").takeIf { it.exists() }
            if (tar != null) {
                RootShell.exec("mkdir -p '/data/user_de/0/$pkg' ; (cd '/data/user_de/0/$pkg' && tar -xzf '${tar.absolutePath}' 2>/dev/null)")
                if (uid != null) {
                    RootShell.exec("chown -R ${uid.first}:${uid.second} '/data/user_de/0/$pkg' 2>/dev/null ; chmod 755 '/data/user_de/0/$pkg' 2>/dev/null")
                }
                RootShell.exec("restorecon -FR '/data/user_de/0/$pkg' 2>/dev/null")
            }
        }
        untar(File(srcDir, "ext.tar.gz"), "/sdcard/Android/data/$pkg")
        untar(File(srcDir, "media.tar.gz"), "/sdcard/Android/media/$pkg")
        untar(File(srcDir, "obb.tar.gz"), "/sdcard/Android/obb/$pkg")

        if (restoreIdentity) {
            val ssaidFile = File(srcDir, "ssaid.txt")
            if (ssaidFile.exists()) {
                val ssaid = ssaidFile.readText().trim()
                if (ssaid.isNotEmpty()) {
                    val xml = "/data/system/users/0/settings_ssaid.xml"
                    val id = uid?.first ?: 10000
                    RootShell.exec(
                        "if [ -f '$xml' ]; then if grep -q 'name=\"$pkg\"' '$xml'; then " +
                            "sed -i 's/name=\"$pkg\" value=\"[^\"]*\"/name=\"$pkg\" value=\"$ssaid\"/g' '$xml' ; else " +
                            "sed -i 's|</settings>|  <setting id=\"$id\" name=\"$pkg\" value=\"$ssaid\" package=\"$pkg\" defaultValue=\"$ssaid\" defaultSysSet=\"true\" tag=\"null\" />\\n</settings>|' '$xml' ; fi ; " +
                            "chmod 600 '$xml' ; chown system:system '$xml' ; fi"
                    )
                }
            }
            val permFile = File(srcDir, "permissions.txt")
            if (permFile.exists()) {
                permFile.readLines().map { it.trim() }
                    .filter { it.startsWith("android.permission.") }
                    .forEach { RootShell.exec("pm grant $pkg $it 2>/dev/null") }
                log("permissions restored")
            }
        }
    }

    private suspend fun tarDir(src: String, dest: File, key: String, sizes: MutableMap<String, Long>, log: (String) -> Unit) {
        val d = "$"
        dest.delete()
        RootShell.exec(
            "if [ -d '$src' ] && [ -n \"${d}(ls -A '$src' 2>/dev/null)\" ]; then " +
                "(cd '$src' && tar -czf '${dest.absolutePath}' --exclude=cache . 2>/dev/null) ; " +
                "chmod 644 '${dest.absolutePath}' ; fi"
        )
        if (dest.exists()) {
            sizes[key] = dest.length()
            log("$key: ${dest.length() / 1024} KB")
        }
    }

    private suspend fun untar(tar: File, dest: String) {
        if (!tar.exists()) return
        RootShell.exec("mkdir -p '$dest' ; (cd '$dest' && tar -xzf '${tar.absolutePath}' 2>/dev/null)")
    }
}
