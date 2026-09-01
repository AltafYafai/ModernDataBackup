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

    fun isRootAvailable(): Boolean {
        rootCached?.let { return it }
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

    fun executeCommand(command: String, useRoot: Boolean = true): CommandResult {
        val outLines = mutableListOf<String>()
        return try {
            val process = if (useRoot) {
                ProcessBuilder("su").redirectErrorStream(true).start()
            } else {
                ProcessBuilder("sh").redirectErrorStream(true).start()
            }

            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()
            }

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { outLines.add(it) }
                }
            }

            val exitCode = process.waitFor()
            CommandResult(
                isSuccess = exitCode == 0,
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
