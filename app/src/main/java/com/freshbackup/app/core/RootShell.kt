package com.freshbackup.app.core

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Minimal root access layer on top of libsu. All calls must run on Dispatchers.IO. */
object RootShell {

    data class RootInfo(
        val granted: Boolean = false,
        val manager: String = "none",
        val suPath: String = "",
        val uid: String = "",
        val selinux: String = "",
        val detail: String = ""
    )

    /** Full root diagnosis for the status card + diagnostics screen. */
    suspend fun details(): RootInfo = withContext(Dispatchers.IO) {
        try {
            val uidRes = Shell.cmd("id -u; echo ---; which su; echo ---; getenforce 2>/dev/null").exec()
            val parts = uidRes.out.joinToString("\n").split("\n---\n")
            val uid = (parts.getOrNull(0) ?: "").trim()
            val suPath = (parts.getOrNull(1) ?: "").trim().lineSequence().firstOrNull()?.trim() ?: ""
            val selinux = (parts.getOrNull(2) ?: "").trim().lineSequence().firstOrNull()?.trim() ?: ""
            if (uid != "0") {
                val hasSu = suPath.isNotEmpty()
                return@withContext RootInfo(
                    granted = false,
                    suPath = suPath,
                    uid = uid,
                    selinux = selinux,
                    detail = if (hasSu) "denied" else "no-su"
                )
            }
            // We are root: identify the manager by its working directory.
            val probe = Shell.cmd(
                "for d in /data/adb/ap /data/adb/magisk /data/adb/ksu; do [ -d \"\$d\" ] && echo \$d; done"
            ).exec()
            val hits = probe.out.map { it.trim() }
            val manager = when {
                hits.any { it.endsWith("/ap") } -> "APatch"
                hits.any { it.endsWith("/magisk") } -> "Magisk"
                hits.any { it.endsWith("/ksu") } -> "KernelSU"
                else -> "root (unknown manager)"
            }
            RootInfo(granted = true, manager = manager, suPath = suPath, uid = uid, selinux = selinux)
        } catch (e: Exception) {
            RootInfo(detail = "shell error: ${e.message}")
        }
    }

    @Volatile
    private var cached: Boolean? = null

    suspend fun isRooted(refresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!refresh) cached?.let { return@withContext it }
        // NOTE: libsu runs commands as the app user when root is unavailable,
        // so exit code alone proves nothing — the UID must actually be 0.
        val ok = try {
            val r = Shell.cmd("id -u").exec()
            r.isSuccess && r.out.firstOrNull()?.trim() == "0"
        } catch (e: Exception) {
            false
        }
        cached = ok
        ok
    }

    /** Human-readable shell probe for the diagnostics screen. */
    suspend fun probe(): String = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("id").exec()
            if (!r.isSuccess) return@withContext "shell failed to start"
            val id = r.out.firstOrNull()?.trim() ?: "no output"
            val rooted = isRooted()
            "id: $id · root=${if (rooted) "YES" else "no"}"
        } catch (e: Exception) {
            "shell error: ${e.message}"
        }
    }

    suspend fun exec(command: String): Shell.Result = withContext(Dispatchers.IO) {
        Shell.cmd(command).exec()
    }

    suspend fun mkdir(path: String) {
        exec("mkdir -p '$path'")
    }

    suspend fun forceStop(pkg: String) {
        exec("am force-stop $pkg")
    }

    suspend fun appUid(pkg: String): Pair<Int, Int>? {
        val res = exec("stat -c '%u:%g' /data/user/0/$pkg 2>/dev/null || stat -c '%u:%g' /data/data/$pkg 2>/dev/null")
        if (!res.isSuccess || res.out.isEmpty()) return null
        val parts = res.out.first().trim().split(":")
        if (parts.size != 2) return null
        val uid = parts[0].toIntOrNull() ?: return null
        val gid = parts[1].toIntOrNull() ?: return null
        return if (uid > 1000) uid to gid else null
    }
}
