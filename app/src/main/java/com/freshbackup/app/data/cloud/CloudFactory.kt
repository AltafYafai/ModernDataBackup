package com.freshbackup.app.data.cloud

import com.freshbackup.app.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Builds a configured backend from the JSON stored in Settings. */
@Singleton
class CloudFactory @Inject constructor(
    private val settings: Settings,
    private val local: LocalBackend
) {

    suspend fun active(): StorageBackend = withContext(Dispatchers.IO) {
        val id = settings.activeBackend()
        build(id, settings.cloudConfig(id), File(settings.backupDir())) ?: local.also {
            it.configure(File(settings.backupDir()))
        }
    }

    suspend fun buildById(id: String): StorageBackend? = withContext(Dispatchers.IO) {
        build(id, settings.cloudConfig(id), File(settings.backupDir()))
    }

    fun build(id: String, json: String, localDir: File): StorageBackend? {
        return try {
            when (id) {
                "local" -> local.also { it.configure(localDir) }
                "webdav" -> {
                    val o = JSONObject(json)
                    WebDavBackend(o.optString("url"), o.optString("user"), o.optString("pass"))
                }
                "s3" -> {
                    val o = JSONObject(json)
                    S3Backend(
                        S3Config(
                            endpoint = o.optString("endpoint"),
                            region = o.optString("region", "us-east-1").ifEmpty { "us-east-1" },
                            bucket = o.optString("bucket"),
                            accessKey = o.optString("access"),
                            secretKey = o.optString("secret"),
                            prefix = o.optString("prefix", "FreshBackup/").ifEmpty { "FreshBackup/" }
                        )
                    )
                }
                "ftp" -> {
                    val o = JSONObject(json)
                    FtpBackend(
                        FtpConfig(
                            host = o.optString("host"),
                            port = o.optInt("port", 21).takeIf { it > 0 } ?: 21,
                            user = o.optString("user"),
                            pass = o.optString("pass"),
                            baseDir = o.optString("base", "/FreshBackup").ifEmpty { "/FreshBackup" },
                            tls = o.optBoolean("tls", true)
                        )
                    )
                }
                "sftp" -> {
                    val o = JSONObject(json)
                    SftpBackend(
                        SftpConfig(
                            host = o.optString("host"),
                            port = o.optInt("port", 22).takeIf { it > 0 } ?: 22,
                            user = o.optString("user"),
                            pass = o.optString("pass"),
                            baseDir = o.optString("base", "/FreshBackup").ifEmpty { "/FreshBackup" }
                        )
                    )
                }
                "smb" -> {
                    val o = JSONObject(json)
                    SmbBackend(
                        SmbConfig(
                            host = o.optString("host"),
                            share = o.optString("share"),
                            domain = o.optString("domain"),
                            user = o.optString("user"),
                            pass = o.optString("pass"),
                            baseDir = o.optString("base", "FreshBackup").ifEmpty { "FreshBackup" }
                        )
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
