package com.freshbackup.app.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pluggable backup destinations. Local disk ships first; WebDAV
 * (Nextcloud/ownCloud/NAS) is fully implemented. Drive, S3, SMB, SFTP
 * and FTP follow the same interface (tracked next).
 */
interface StorageBackend {
    val id: String
    val displayName: String
    suspend fun upload(local: File, remotePath: String)
    suspend fun download(remotePath: String, local: File)
    suspend fun list(remotePath: String): List<String>
    suspend fun test(): Boolean
}

@Singleton
class LocalBackend @Inject constructor() : StorageBackend {
    override val id = "local"
    override val displayName = "This device"
    private var root: File = File("/sdcard/FreshBackup")

    fun configure(rootDir: File) {
        root = rootDir
    }

    override suspend fun upload(local: File, remotePath: String) = withContext(Dispatchers.IO) {
        val dst = File(root, remotePath)
        dst.parentFile?.mkdirs()
        if (local.isDirectory) local.copyRecursively(dst, overwrite = true)
        else local.copyTo(dst, overwrite = true)
    }

    override suspend fun download(remotePath: String, local: File) = withContext(Dispatchers.IO) {
        val src = File(root, remotePath)
        if (src.isDirectory) src.copyRecursively(local, overwrite = true)
        else src.copyTo(local, overwrite = true)
    }

    override suspend fun list(remotePath: String): List<String> = withContext(Dispatchers.IO) {
        File(root, remotePath).list()?.toList() ?: emptyList()
    }

    override suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        root.mkdirs()
        root.canWrite()
    }
}

class WebDavBackend(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) : StorageBackend {
    override val id = "webdav"
    override val displayName = "WebDAV"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun request(path: String, method: String, body: okhttp3.RequestBody? = null): Request {
        val url = baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        return Request.Builder().url(url)
            .header("Authorization", Credentials.basic(username, password))
            .method(method, body)
            .build()
    }

    override suspend fun upload(local: File, remotePath: String) = withContext(Dispatchers.IO) {
        local.inputStream().use { ins ->
            val body = ins.readBytes().toRequestBody("application/octet-stream".toMediaTypeOrNull())
            client.newCall(request(remotePath, "PUT", body)).execute().use { resp ->
                check(resp.isSuccessful) { "upload failed: ${resp.code}" }
            }
        }
    }

    override suspend fun download(remotePath: String, local: File) = withContext(Dispatchers.IO) {
        client.newCall(request(remotePath, "GET")).execute().use { resp ->
            check(resp.isSuccessful) { "download failed: ${resp.code}" }
            local.parentFile?.mkdirs()
            local.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
        }
    }

    override suspend fun list(remotePath: String): List<String> = withContext(Dispatchers.IO) {
        val body = "<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><prop><displayname/></prop></propfind>"
            .toRequestBody("application/xml".toMediaTypeOrNull())
        client.newCall(request(remotePath, "PROPFIND", body)).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val xml = resp.body!!.string()
            Regex("<d?:href>(.*?)</d?:href>").findAll(xml)
                .map { it.groupValues[1].substringAfterLast("/").substringBefore("?") }
                .filter { it.isNotEmpty() }
                .toList()
        }
    }

    override suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val body = "".toRequestBody("application/xml".toMediaTypeOrNull())
            client.newCall(request("/", "PROPFIND", body)).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }
}
