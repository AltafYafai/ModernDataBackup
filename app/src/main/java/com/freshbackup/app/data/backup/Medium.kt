package com.freshbackup.app.data.backup

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.CallLog
import android.provider.Telephony
import com.freshbackup.app.core.Crypto
import com.freshbackup.app.core.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device-level backups that need no root: SMS, call log, wallpaper,
 * folders (incremental). WiFi config needs root.
 */
@Singleton
class Medium @Inject constructor(@ApplicationContext private val context: Context) {

    fun dir(root: File): File = File(root, "_medium").apply { mkdirs() }

    // ---- SMS ----

    suspend fun backupSms(dir: File, userId: String, passphrase: String): Int = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf("address", "body", "date", "type", "read"),
            null, null, "date ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("address", c.getString(0) ?: "")
                    put("body", c.getString(1) ?: "")
                    put("date", c.getLong(2))
                    put("type", c.getInt(3))
                    put("read", c.getInt(4))
                })
            }
        }
        writeJson(dir, "sms.json", arr, userId, passphrase)
        arr.length()
    }

    suspend fun restoreSms(dir: File, userId: String, passphrase: String): Int = withContext(Dispatchers.IO) {
        val arr = readJson(dir, "sms.json", userId, passphrase) ?: return@withContext 0
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val v = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, o.optString("address"))
                put(Telephony.Sms.BODY, o.optString("body"))
                put(Telephony.Sms.DATE, o.optLong("date"))
                put(Telephony.Sms.TYPE, o.optInt("type"))
                put(Telephony.Sms.READ, o.optInt("read"))
            }
            try {
                context.contentResolver.insert(Telephony.Sms.CONTENT_URI, v)
                n++
            } catch (e: Exception) { /* requires default-SMS role */ }
        }
        n
    }

    // ---- Call log ----

    suspend fun backupCalls(dir: File, userId: String, passphrase: String): Int = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME),
            null, null, "${CallLog.Calls.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("number", c.getString(0) ?: "")
                    put("date", c.getLong(1))
                    put("duration", c.getLong(2))
                    put("type", c.getInt(3))
                    put("name", c.getString(4) ?: "")
                })
            }
        }
        writeJson(dir, "calls.json", arr, userId, passphrase)
        arr.length()
    }

    suspend fun restoreCalls(dir: File, userId: String, passphrase: String): Int = withContext(Dispatchers.IO) {
        val arr = readJson(dir, "calls.json", userId, passphrase) ?: return@withContext 0
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val v = ContentValues().apply {
                put(CallLog.Calls.NUMBER, o.optString("number"))
                put(CallLog.Calls.DATE, o.optLong("date"))
                put(CallLog.Calls.DURATION, o.optLong("duration"))
                put(CallLog.Calls.TYPE, o.optInt("type"))
                put(CallLog.Calls.CACHED_NAME, o.optString("name"))
            }
            try {
                context.contentResolver.insert(CallLog.Calls.CONTENT_URI, v)
                n++
            } catch (e: Exception) { }
        }
        n
    }

    // ---- Wallpaper (stored plaintext, like Swift Backup) ----

    suspend fun backupWallpaper(dir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val drawable = WallpaperManager.getInstance(context).drawable ?: return@withContext false
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            FileOutputStream(File(dir, "wallpaper.png")).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun restoreWallpaper(dir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val f = File(dir, "wallpaper.png")
            if (!f.exists()) return@withContext false
            val wm = WallpaperManager.getInstance(context)
            f.inputStream().use { ins ->
                if (Build.VERSION.SDK_INT >= 24) {
                    wm.setStream(ins, null, true, WallpaperManager.FLAG_SYSTEM)
                } else {
                    @Suppress("DEPRECATION")
                    wm.setStream(ins)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- WiFi (root) ----

    suspend fun backupWifi(dir: File): Boolean {
        if (!RootShell.isRooted()) return false
        val dest = File(dir, "wifi.xml")
        RootShell.exec("cp -f /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml '${dest.absolutePath}' 2>/dev/null")
        RootShell.exec("cp -f /data/misc/wifi/WifiConfigStore.xml '${dest.absolutePath}' 2>/dev/null")
        RootShell.exec("chmod 644 '${dest.absolutePath}' 2>/dev/null")
        return dest.exists()
    }

    suspend fun restoreWifi(dir: File): Boolean {
        val src = File(dir, "wifi.xml")
        if (!src.exists() || !RootShell.isRooted()) return false
        RootShell.exec(
            "cp -f '${src.absolutePath}' /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml 2>/dev/null ; " +
                "cp -f '${src.absolutePath}' /data/misc/wifi/WifiConfigStore.xml 2>/dev/null ; " +
                "chown wifi:wifi /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml /data/misc/wifi/WifiConfigStore.xml 2>/dev/null ; " +
                "chmod 660 /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml /data/misc/wifi/WifiConfigStore.xml 2>/dev/null"
        )
        RootShell.exec("svc wifi disable ; sleep 1 ; svc wifi enable 2>/dev/null")
        return true
    }

    // ---- Folders (incremental by size+mtime manifest) ----

    suspend fun backupFolder(src: File, dir: File, tag: String, log: (String) -> Unit): Int = withContext(Dispatchers.IO) {
        require(src.exists()) { "missing: ${src.path}" }
        val dest = File(dir, "folders/$tag").apply { mkdirs() }
        val manifestFile = File(dest, ".manifest.json")
        val prev = try {
            JSONObject(manifestFile.readText())
        } catch (e: Exception) {
            JSONObject()
        }
        val next = JSONObject()
        var copied = 0
        val files = src.walkTopDown().filter { it.isFile }.toList()
        files.forEach { f ->
            val rel = f.relativeTo(src).path
            val key = "${f.length()}:${f.lastModified()}"
            next.put(rel, key)
            if (prev.optString(rel, "") != key) {
                val dst = File(dest, rel)
                dst.parentFile?.mkdirs()
                f.copyTo(dst, overwrite = true)
                copied++
            }
        }
        manifestFile.writeText(next.toString())
        log("$tag: $copied changed / ${files.size}")
        copied
    }

    suspend fun restoreFolder(dir: File, tag: String, dest: File): Int = withContext(Dispatchers.IO) {
        val src = File(dir, "folders/$tag")
        require(src.exists()) { "no backup: $tag" }
        var n = 0
        src.walkTopDown().filter { it.isFile && it.name != ".manifest.json" }.forEach { f ->
            val dst = File(dest, f.relativeTo(src).path)
            dst.parentFile?.mkdirs()
            f.copyTo(dst, overwrite = true)
            n++
        }
        n
    }

    // ---- JSON helpers (encrypted when an account id is set) ----

    private fun writeJson(dir: File, name: String, arr: JSONArray, userId: String, passphrase: String) {
        dir.mkdirs()
        val raw = File(dir, name)
        raw.writeText(arr.toString())
        if (userId.isNotEmpty()) {
            Crypto.encrypt(raw, File(dir, Crypto.encryptedName(name)), userId, passphrase)
            raw.delete()
        }
    }

    private fun readJson(dir: File, name: String, userId: String, passphrase: String): JSONArray? {
        val enc = File(dir, Crypto.encryptedName(name))
        return try {
            if (enc.exists() && userId.isNotEmpty()) {
                val raw = File(dir, name)
                Crypto.decrypt(enc, raw, userId, passphrase)
                JSONArray(raw.readText())
            } else {
                val raw = File(dir, name)
                if (!raw.exists()) return null
                JSONArray(raw.readText())
            }
        } catch (e: Exception) {
            null
        }
    }
}
