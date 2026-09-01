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
        // Strategy 1: dumpsys package <pkg>
        val dumpRes = executeCommand("dumpsys package $packageName | grep -E 'userId=|appId='", useRoot = true)
        if (dumpRes.isSuccess && dumpRes.out.isNotEmpty()) {
            for (line in dumpRes.out) {
                val cleaned = line.trim()
                val uidStr = if (cleaned.contains("userId=")) cleaned.substringAfter("userId=").substringBefore(" ")
                else if (cleaned.contains("appId=")) cleaned.substringAfter("appId=").substringBefore(" ")
                else ""
                val uid = uidStr.trim().toIntOrNull()
                if (uid != null && uid > 0) return Pair(uid, uid)
            }
        }

        // Strategy 2: stat -c '%u:%g' /data/data/$packageName
        val res = executeCommand("stat -c '%u:%g' /data/data/$packageName", useRoot = true)
        if (res.isSuccess && res.out.isNotEmpty()) {
            val parts = res.out.first().trim().split(":")
            if (parts.size == 2) {
                val uid = parts[0].toIntOrNull()
                val gid = parts[1].toIntOrNull()
                if (uid != null && gid != null && uid > 0) return Pair(uid, gid)
            }
        }

        // Strategy 3: ls -nd /data/data/$packageName
        val lsRes = executeCommand("ls -nd /data/data/$packageName", useRoot = true)
        if (lsRes.isSuccess && lsRes.out.isNotEmpty()) {
            val tokens = lsRes.out.first().split("\\s+".toRegex())
            val uid = tokens.getOrNull(2)?.toIntOrNull()
            val gid = tokens.getOrNull(3)?.toIntOrNull()
            if (uid != null && gid != null && uid > 0) return Pair(uid, gid)
        }

        return null
    }

    fun getAppSsaid(packageName: String): String? {
        val cmd = "grep -B 1 '$packageName' /data/system/users/0/settings_ssaid.xml 2>/dev/null | grep 'value=' | sed 's/.*value=\"//;s/\".*//'"
        val res = executeCommand(cmd, useRoot = true)
        if (res.isSuccess && res.out.isNotEmpty() && res.out.first().isNotBlank()) {
            return res.out.first().trim()
        }
        val ssaidRes = executeCommand("settings get --user 0 secure android_id", useRoot = true)
        if (ssaidRes.isSuccess && ssaidRes.out.isNotEmpty() && ssaidRes.out.first().isNotBlank()) {
            return ssaidRes.out.first().trim()
        }
        return null
    }

    fun restoreAppSsaid(packageName: String, ssaid: String) {
        if (ssaid.isBlank()) return
        val uid = getAppUid(packageName)?.first
        if (uid != null) {
            val cmd = "sed -i '/name=\"$packageName\"/!b;n;c\\    <setting id=\"$uid\" name=\"$packageName\" value=\"$ssaid\" package=\"$packageName\" />' /data/system/users/0/settings_ssaid.xml"
            executeCommand(cmd, useRoot = true)
        }
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

    fun getAppOps(packageName: String): List<String> {
        val cmd = "appops get $packageName"
        val res = executeCommand(cmd, useRoot = true)
        return if (res.isSuccess) res.out.filter { it.contains(": allow") } else emptyList()
    }

    fun restoreAppOp(packageName: String, opLine: String) {
        val op = opLine.substringBefore(":").trim()
        if (op.isNotBlank()) {
            executeCommand("appops set $packageName $op allow", useRoot = true)
        }
    }

    fun forceStopApp(packageName: String) {
        executeCommand("am force-stop $packageName", useRoot = true)
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
