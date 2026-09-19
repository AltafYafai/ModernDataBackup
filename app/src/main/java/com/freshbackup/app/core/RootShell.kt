package com.freshbackup.app.core

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Minimal root access layer on top of libsu. All calls must run on Dispatchers.IO. */
object RootShell {

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
