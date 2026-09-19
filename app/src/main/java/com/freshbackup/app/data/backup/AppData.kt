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
        includeIdentity: Boolean,
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

        // SSAID identity + granted permissions + keystore for faithful restore.
        if (includeIdentity && (includeExt || includeData)) {
            val uid = RootShell.appUid(pkg)?.first
            if (uid != null) {
                File(destDir, "uid.txt").writeText(uid.toString())
                backupKeystore(uid, destDir, log)
            }
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

    /** AndroidKeyStore keys live under the app UID; archive them for relogin-free restore. */
    private suspend fun backupKeystore(uid: Int, destDir: File, log: (String) -> Unit) {
        val dest = File(destDir, "keystore.tar.gz")
        dest.delete()
        val d = "$"
        RootShell.exec(
            "if [ -d /data/misc/keystore/user_0 ]; then " +
                "KS=${d}(ls /data/misc/keystore/user_0 2>/dev/null | grep '^${uid}_') ; " +
                "if [ -n \"${d}KS\" ]; then (cd /data/misc/keystore/user_0 && tar -czf '${dest.absolutePath}' ${d}KS 2>/dev/null) ; " +
                "chmod 644 '${dest.absolutePath}' ; fi ; fi"
        )
        if (dest.exists()) {
            log("keystore: ${dest.length() / 1024} KB")
        }
    }

    private suspend fun restoreKeystore(pkg: String, srcDir: File, newUid: Int?, log: (String) -> Unit) {
        val tar = File(srcDir, "keystore.tar.gz")
        if (!tar.exists()) return
        val oldUid = try {
            File(srcDir, "uid.txt").readText().trim()
        } catch (e: Exception) {
            ""
        }
        val target = newUid?.toString() ?: return
        val d = "$"
        RootShell.exec(
            "mkdir -p /data/misc/keystore/user_0 ; " +
                "(tar -xzf '${tar.absolutePath}' -C /data/misc/keystore/user_0 2>/dev/null || tar -xf '${tar.absolutePath}' -C /data/misc/keystore/user_0 2>/dev/null) ; " +
                "if [ -n \"$oldUid\" ] && [ \"$oldUid\" != \"$target\" ]; then " +
                "for f in /data/misc/keystore/user_0/${oldUid}_*; do " +
                "if [ -f \"${d}f\" ]; then N=${d}(echo \"${d}f\" | sed \"s/${oldUid}_/${target}_/\") ; mv \"${d}f\" \"${d}N\" 2>/dev/null ; fi ; done ; fi ; " +
                "chown -R keystore:keystore /data/misc/keystore/user_0 2>/dev/null"
        )
        log("keystore restored")
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
        if (uid == null) {
            log("WARNING: UID of $pkg unknown — ownership fix skipped, app may crash on launch")
        }

        if (restoreData) {
            val tar = File(srcDir, "data.tar.gz").takeIf { it.exists() }
            if (tar != null) {
                val d = "$"
                // -xzf first (gzip), -xf fallback (plain tar: some toolchains lack gzip)
                RootShell.exec(
                    "mkdir -p '/data/user/0/$pkg' '/data/data/$pkg' ; " +
                        "LIB=${d}(readlink /data/user/0/$pkg/lib || readlink /data/data/$pkg/lib) ; " +
                        "(cd '/data/user/0/$pkg' && (tar -xzf '${tar.absolutePath}' 2>/dev/null || tar -xf '${tar.absolutePath}' 2>/dev/null)) ; " +
                        "(cd '/data/data/$pkg' && (tar -xzf '${tar.absolutePath}' 2>/dev/null || tar -xf '${tar.absolutePath}' 2>/dev/null)) ; " +
                        "if [ -n \"${d}LIB\" ]; then ln -sfn \"${d}LIB\" '/data/user/0/$pkg/lib' ; ln -sfn \"${d}LIB\" '/data/data/$pkg/lib' ; fi"
                )
                if (uid != null) {
                    RootShell.exec(
                        "chown -R ${uid.first}:${uid.second} '/data/user/0/$pkg' '/data/data/$pkg' 2>/dev/null ; " +
                            "chmod 755 '/data/user/0/$pkg' '/data/data/$pkg' 2>/dev/null"
                    )
                }
                RootShell.exec("restorecon -FR '/data/user/0/$pkg' '/data/data/$pkg' 2>/dev/null")
                val count = RootShell.exec("ls -A '/data/user/0/$pkg' 2>/dev/null | wc -l").out.firstOrNull()?.trim()
                log("data restored ($count entries)")
            } else {
                log("no data archive in backup")
            }
        }
        if (restoreDe) {
            val tar = File(srcDir, "data_de.tar.gz").takeIf { it.exists() }
            if (tar != null) {
                RootShell.exec("mkdir -p '/data/user_de/0/$pkg' ; (cd '/data/user_de/0/$pkg' && (tar -xzf '${tar.absolutePath}' 2>/dev/null || tar -xf '${tar.absolutePath}' 2>/dev/null))")
                if (uid != null) {
                    RootShell.exec("chown -R ${uid.first}:${uid.second} '/data/user_de/0/$pkg' 2>/dev/null ; chmod 755 '/data/user_de/0/$pkg' 2>/dev/null")
                }
                RootShell.exec("restorecon -FR '/data/user_de/0/$pkg' 2>/dev/null")
                log("protected data restored")
            }
        }
        untar(File(srcDir, "ext.tar.gz"), "/sdcard/Android/data/$pkg", "external data", log)
        untar(File(srcDir, "media.tar.gz"), "/sdcard/Android/media/$pkg", "media", log)
        untar(File(srcDir, "obb.tar.gz"), "/sdcard/Android/obb/$pkg", "obb", log)

        if (restoreIdentity) {
            restoreKeystore(pkg, srcDir, uid?.first, log)
        }

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

    private suspend fun untar(tar: File, dest: String, label: String, log: (String) -> Unit) {
        if (!tar.exists()) return
        RootShell.exec("mkdir -p '$dest' ; (cd '$dest' && (tar -xzf '${tar.absolutePath}' 2>/dev/null || tar -xf '${tar.absolutePath}' 2>/dev/null))")
        val count = RootShell.exec("ls -A '$dest' 2>/dev/null | wc -l").out.firstOrNull()?.trim()
        log("$label restored ($count entries)")
    }
}
