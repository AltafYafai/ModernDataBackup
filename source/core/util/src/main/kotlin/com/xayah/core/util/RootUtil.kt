package com.xayah.core.util

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

data class CommandResult(
    val isSuccess: Boolean,
    val out: List<String>,
    val code: Int
)

object RootUtil {
    private var rootCached: Boolean? = null
    private var rootTypeCached: String? = null

    fun isRootAvailable(forceCheck: Boolean = false): Boolean {
        if (!forceCheck && rootCached != null) return rootCached!!
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su"
        )
        val fileExists = paths.any { File(it).exists() }
        val checkExec = if (fileExists) {
            executeCommand("id", useRoot = true).isSuccess
        } else {
            false
        }
        rootCached = checkExec
        return checkExec
    }

    fun requestRoot(): Boolean {
        rootCached = null
        val result = executeCommand("id", useRoot = true)
        rootCached = result.isSuccess
        return result.isSuccess
    }

    fun getRootType(): String {
        rootTypeCached?.let { return it }
        if (!isRootAvailable()) {
            rootTypeCached = "Not Rooted"
            return rootTypeCached!!
        }
        val type = when {
            File("/data/adb/ksu").exists() -> "KernelSU"
            File("/data/adb/ap").exists() -> "APatch"
            File("/data/adb/magisk").exists() -> "Magisk"
            executeCommand("which magisk", useRoot = true).isSuccess -> "Magisk"
            else -> "SuperSU / SU Binary"
        }
        rootTypeCached = type
        return type
    }

    fun getSelinuxMode(): String {
        val result = executeCommand("getenforce", useRoot = isRootAvailable())
        return if (result.isSuccess && result.out.isNotEmpty()) {
            result.out.first().trim()
        } else {
            "Enforcing"
        }
    }

    fun getAppUid(packageName: String): Pair<Int, Int>? {
        val cmd = "stat -c '%u:%g' /data/data/$packageName"
        val res = executeCommand(cmd, useRoot = true)
        if (res.isSuccess && res.out.isNotEmpty()) {
            val parts = res.out.first().trim().split(":")
            if (parts.size == 2) {
                val uid = parts[0].toIntOrNull()
                val gid = parts[1].toIntOrNull()
                if (uid != null && gid != null) return Pair(uid, gid)
            }
        }
        val dumpRes = executeCommand("dumpsys package $packageName | grep userId=", useRoot = true)
        if (dumpRes.isSuccess && dumpRes.out.isNotEmpty()) {
            val line = dumpRes.out.first()
            val uid = line.substringAfter("userId=").substringBefore(" ").toIntOrNull()
            if (uid != null) return Pair(uid, uid)
        }
        return null
    }

    fun getGrantedPermissions(packageName: String): List<String> {
        val cmd = "dumpsys package $packageName | grep 'permission.*granted=true'"
        val res = executeCommand(cmd, useRoot = true)
        val perms = mutableListOf<String>()
        if (res.isSuccess) {
            res.out.forEach { line ->
                val perm = line.substringBefore(": granted=true").trim().substringAfterLast(" ")
                if (perm.startsWith("android.permission.")) {
                    perms.add(perm)
                }
            }
        }
        return perms
    }

    fun grantPermission(packageName: String, permission: String): Boolean {
        val cmd = "pm grant $packageName $permission"
        return executeCommand(cmd, useRoot = true).isSuccess
    }

    fun executeCommand(command: String, useRoot: Boolean = true): CommandResult {
        val outLines = mutableListOf<String>()
        return try {
            val process = if (useRoot) {
                try {
                    ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                } catch (e: Exception) {
                    val p = ProcessBuilder("su").redirectErrorStream(true).start()
                    DataOutputStream(p.outputStream).use { os ->
                        os.writeBytes("$command\n")
                        os.writeBytes("exit\n")
                        os.flush()
                    }
                    p
                }
            } else {
                ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            }

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { outLines.add(it) }
                }
            }

            val exitCode = process.waitFor()
            // In unix/toybox tools (e.g. tar or cp), exit code 0 or 1 with non-critical warnings is common
            CommandResult(
                isSuccess = exitCode == 0 || exitCode == 1,
                out = outLines,
                code = exitCode
            )
        } catch (e: Exception) {
            CommandResult(
                isSuccess = false,
                out = listOf(e.message ?: "Unknown error"),
                code = -1
            )
        }
    }
}
