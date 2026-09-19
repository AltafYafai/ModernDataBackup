package com.freshbackup.app.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

// ---------------------------------------------------------------------------
// S3-compatible storage (AWS, MinIO, …) — SigV4 signed REST over OkHttp.
// ---------------------------------------------------------------------------

data class S3Config(
    val endpoint: String = "",
    val region: String = "us-east-1",
    val bucket: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val prefix: String = "FreshBackup/"
)

class S3Backend(val config: S3Config) : StorageBackend {
    override val id = "s3"
    override val displayName = "S3 storage"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun sha256Hex(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256")
        return d.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun fileSha256(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        FileInputStream(f).use { ins ->
            val buf = ByteArray(128 * 1024)
            var n: Int
            while (ins.read(buf).also { n = it } != -1) d.update(buf, 0, n)
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%7E", "~")

    private fun encPath(path: String): String =
        path.split("/").joinToString("/") { enc(it) }

    private fun sign(method: String, url: String, query: String, payloadHash: String): Request.Builder {
        val uri = URI(url)
        val host = if (uri.port != -1 && uri.port != 443 && uri.port != 80) "${uri.host}:${uri.port}" else uri.host
        val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val amzDate = sdf.format(Date())
        val dateStamp = amzDate.substring(0, 8)
        val canonicalUri = if (uri.rawPath.isNullOrEmpty()) "/" else uri.rawPath
        val signedHeaders = "host;x-amz-content-sha256;x-amz-date"
        val canonicalHeaders = "host:$host\nx-amz-content-sha256:$payloadHash\nx-amz-date:$amzDate\n"
        val canonical = "$method\n$canonicalUri\n$query\n$canonicalHeaders\n$signedHeaders\n$payloadHash"
        val scope = "$dateStamp/${config.region}/s3/aws4_request"
        val toSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonical.toByteArray())}"
        var k = hmac(("AWS4" + config.secretKey).toByteArray(), dateStamp)
        k = hmac(k, config.region)
        k = hmac(k, "s3")
        k = hmac(k, "aws4_request")
        val sig = hmac(k, toSign).joinToString("") { "%02x".format(it) }
        val fullUrl = if (query.isEmpty()) url else "$url?$query"
        return Request.Builder().url(fullUrl)
            .header("x-amz-date", amzDate)
            .header("x-amz-content-sha256", payloadHash)
            .header("Authorization", "AWS4-HMAC-SHA256 Credential=${config.accessKey}/$scope, SignedHeaders=$signedHeaders, Signature=$sig")
    }

    private fun objectUrl(key: String): String =
        config.endpoint.trimEnd('/') + "/" + config.bucket + "/" + encPath(key)

    private fun keyFor(remotePath: String): String {
        val p = config.prefix.trim { it == '/' }
        return if (p.isEmpty()) remotePath.trimStart('/') else "$p/${remotePath.trimStart('/')}"
    }

    override suspend fun upload(local: File, remotePath: String) = withContext(Dispatchers.IO) {
        val hash = fileSha256(local)
        val req = sign("PUT", objectUrl(keyFor(remotePath)), "", hash)
            .put(local.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "S3 upload failed: ${resp.code}" }
        }
        Unit
    }

    override suspend fun download(remotePath: String, local: File) = withContext(Dispatchers.IO) {
        val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val req = sign("GET", objectUrl(keyFor(remotePath)), "", empty).get().build()
        client.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "S3 download failed: ${resp.code}" }
            local.parentFile?.mkdirs()
            local.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
        }
        Unit
    }

    override suspend fun list(remotePath: String): List<String> = withContext(Dispatchers.IO) {
        val prefix = keyFor(remotePath).trimEnd('/') + "/"
        val query = "list-type=2&max-keys=1000&prefix=${enc(prefix)}"
        val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val base = config.endpoint.trimEnd('/') + "/" + config.bucket + "/"
        val req = sign("GET", base, query, empty).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val xml = resp.body!!.string()
            Regex("<Key>(.*?)</Key>").findAll(xml)
                .map { it.groupValues[1].removePrefix(prefix).replace("&amp;", "&") }
                .filter { it.isNotEmpty() && !it.contains("/") }
                .toList()
        }
    }

    override suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        runCatching { list("") }.isSuccess
    }
}

// ---------------------------------------------------------------------------
// FTP / FTPS (commons-net)
// ---------------------------------------------------------------------------

data class FtpConfig(
    val host: String = "",
    val port: Int = 21,
    val user: String = "",
    val pass: String = "",
    val baseDir: String = "/FreshBackup",
    val tls: Boolean = true
)

class FtpBackend(val config: FtpConfig) : StorageBackend {
    override val id = "ftp"
    override val displayName = "FTP"

    private fun connect(): org.apache.commons.net.ftp.FTPClient {
        val ftp = if (config.tls) org.apache.commons.net.ftp.FTPSClient(false) else org.apache.commons.net.ftp.FTPClient()
        ftp.connect(config.host, config.port)
        check(ftp.login(config.user, config.pass)) { "FTP login failed" }
        ftp.enterLocalPassiveMode()
        ftp.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
        return ftp
    }

    private fun remote(p: String): String =
        (config.baseDir.trimEnd('/') + "/" + p.trimStart('/')).replace("//", "/")

    private fun ensureDirs(ftp: org.apache.commons.net.ftp.FTPClient, dir: String) {
        var cur = ""
        dir.split("/").filter { it.isNotEmpty() }.forEach { part ->
            cur += "/$part"
            try {
                ftp.makeDirectory(cur)
            } catch (e: Exception) { }
        }
    }

    override suspend fun upload(local: File, remotePath: String) = withContext(Dispatchers.IO) {
        val ftp = connect()
        try {
            ensureDirs(ftp, remote(remotePath).substringBeforeLast("/"))
            FileInputStream(local).use { ins ->
                check(ftp.storeFile(remote(remotePath), ins)) { "FTP upload failed" }
            }
        } finally {
            try {
                ftp.logout()
            } catch (e: Exception) { }
            try {
                ftp.disconnect()
            } catch (e: Exception) { }
        }
        Unit
    }

    override suspend fun download(remotePath: String, local: File) = withContext(Dispatchers.IO) {
        val ftp = connect()
        try {
            local.parentFile?.mkdirs()
            local.outputStream().use { out ->
                check(ftp.retrieveFile(remote(remotePath), out)) { "FTP download failed" }
            }
        } finally {
            try {
                ftp.logout()
            } catch (e: Exception) { }
            try {
                ftp.disconnect()
            } catch (e: Exception) { }
        }
        Unit
    }

    override suspend fun list(remotePath: String): List<String> = withContext(Dispatchers.IO) {
        val ftp = connect()
        try {
            ftp.listFiles(remote(remotePath)).map { it.name }.filter { it != "." && it != ".." }
        } finally {
            try {
                ftp.logout()
            } catch (e: Exception) { }
            try {
                ftp.disconnect()
            } catch (e: Exception) { }
        }
    }

    override suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val ftp = connect()
            try {
                ftp.noop()
            } finally {
                try {
                    ftp.logout()
                } catch (e: Exception) { }
                try {
                    ftp.disconnect()
                } catch (e: Exception) { }
            }
        }.isSuccess
    }
}

// ---------------------------------------------------------------------------
// SFTP (sshj)
// ---------------------------------------------------------------------------

data class SftpConfig(
    val host: String = "",
    val port: Int = 22,
    val user: String = "",
    val pass: String = "",
    val baseDir: String = "/FreshBackup"
)

class SftpBackend(val config: SftpConfig) : StorageBackend {
    override val id = "sftp"
    override val displayName = "SFTP"

    private fun remote(p: String): String =
        (config.baseDir.trimEnd('/') + "/" + p.trimStart('/')).replace("//", "/")

    private fun <T> withClient(block: (net.schmizz.sshj.sftp.SFTPClient) -> T): T {
        val ssh = net.schmizz.sshj.SSHClient()
        ssh.addHostKeyVerifier(net.schmizz.sshj.transport.verification.PromiscuousVerifier())
        ssh.connect(config.host, config.port)
        try {
            ssh.authPassword(config.user, config.pass)
            val sftp = ssh.newSFTPClient()
            try {
                return block(sftp)
            } finally {
                try {
                    sftp.close()
                } catch (e: Exception) { }
            }
        } finally {
            try {
                ssh.disconnect()
            } catch (e: Exception) { }
        }
    }

    override suspend fun upload(local: File, remotePath: String) = withContext(Dispatchers.IO) {
        withClient { sftp ->
            val dest = remote(remotePath)
            try {
                sftp.mkdirs(dest.substringBeforeLast("/"))
            } catch (e: Exception) { }
            sftp.put(local.absolutePath, dest)
        }
        Unit
    }

    override suspend fun download(remotePath: String, local: File) = withContext(Dispatchers.IO) {
        withClient { sftp ->
            local.parentFile?.mkdirs()
            sftp.get(remote(remotePath), local.absolutePath)
        }
        Unit
    }

    override suspend fun list(remotePath: String): List<String> = withContext(Dispatchers.IO) {
        withClient { sftp ->
            sftp.ls(remote(remotePath)).map { it.name }.filter { it != "." && it != ".." }
        }
    }

    override suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        runCatching { withClient { } }.isSuccess
    }
}

// ---------------------------------------------------------------------------
// SMB / Windows shares (smbj)
// ---------------------------------------------------------------------------

data class SmbConfig(
    val host: String = "",
    val share: String = "",
    val domain: String = "",
    val user: String = "",
    val pass: String = "",
    val baseDir: String = "FreshBackup"
)

class SmbBackend(val config: SmbConfig) : StorageBackend {
    override val id = "smb"
    override val displayName = "SMB"

    private fun remote(p: String): String {
        val base = config.baseDir.trimEnd('/').trimStart('/')
        return if (base.isEmpty()) p.trimStart('/') else "$base/${p.trimStart('/')}"
    }

    private fun <T> withShare(block: (com.hierynomus.smbj.share.DiskShare) -> T): T {
        val client = com.hierynomus.smbj.SMBClient()
        val connection = client.connect(config.host)
        try {
            val session = connection.authenticate(
                com.hierynomus.smbj.auth.AuthenticationContext(config.user, config.pass.toCharArray(), config.domain)
            )
            val share = session.connectShare(config.share) as com.hierynomus.smbj.share.DiskShare
            try {
                return block(share)
            } finally {
                try {
                    share.close()
                } catch (e: Exception) { }
            }
        } finally {
            try {
                connection.close()
            } catch (e: Exception) { }
            try {
                client.close()
            } catch (e: Exception) { }
        }
    }

    private fun ensureDirs(share: com.hierynomus.smbj.share.DiskShare, dir: String) {
        var cur = ""
        dir.split("/").filter { it.isNotEmpty() }.forEach { part ->
            cur = if (cur.isEmpty()) part else "$cur/$part"
            try {
                share.mkdir(cur)
            } catch (e: Exception) { }
        }
    }

    private fun openWrite(share: com.hierynomus.smbj.share.DiskShare, path: String) =
        share.openFile(
            path,
            setOf(com.hierynomus.msdtyp.AccessMask.GENERIC_WRITE),
            setOf(com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL),
            com.hierynomus.mssmb2.SMB2ShareAccess.ALL,
            com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OVERWRITE_IF,
            emptySet()
        )

    private fun openRead(share: com.hierynomus.smbj.share.DiskShare, path: String) =
        share.openFile(
            path,
            setOf(com.hierynomus.msdtyp.AccessMask.GENERIC_READ),
            setOf(com.hierynomus.msfscc.FileAttributes.FILE_ATTRIBUTE_NORMAL),
            com.hierynomus.mssmb2.SMB2ShareAccess.ALL,
            com.hierynomus.mssmb2.SMB2CreateDisposition.FILE_OPEN,
            emptySet()
        )

    override suspend fun upload(local: File, remotePath: String) = withContext(Dispatchers.IO) {
        withShare { share ->
            val dest = remote(remotePath)
            ensureDirs(share, dest.substringBeforeLast("/"))
            openWrite(share, dest).use { f ->
                FileInputStream(local).use { ins ->
                    val buf = ByteArray(128 * 1024)
                    var n: Int
                    var offset = 0L
                    while (ins.read(buf).also { n = it } != -1) {
                        f.write(buf.copyOf(n), offset)
                        offset += n
                    }
                }
            }
        }
        Unit
    }

    override suspend fun download(remotePath: String, local: File) = withContext(Dispatchers.IO) {
        withShare { share ->
            local.parentFile?.mkdirs()
            openRead(share, remote(remotePath)).use { f ->
                local.outputStream().use { out ->
                    val buf = ByteArray(128 * 1024)
                    var offset = 0L
                    while (true) {
                        val n = f.read(buf, offset)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        offset += n
                    }
                }
            }
        }
        Unit
    }

    override suspend fun list(remotePath: String): List<String> = withContext(Dispatchers.IO) {
        withShare { share ->
            share.list(remote(remotePath)).map { it.fileName }.filter { it != "." && it != ".." }
        }
    }

    override suspend fun test(): Boolean = withContext(Dispatchers.IO) {
        runCatching { withShare { } }.isSuccess
    }
}

/** Recursive folder sync between local backup root and any backend. */
@Singleton
class CloudSync @Inject constructor() {

    suspend fun uploadDir(localDir: File, backend: StorageBackend, prefix: String, log: (String) -> Unit): Int {
        var n = 0
        val files = localDir.walkTopDown().filter { it.isFile }.toList()
        files.forEachIndexed { i, f ->
            val rel = f.relativeTo(localDir).path.replace(File.separatorChar, '/')
            try {
                backend.upload(f, "$prefix$rel")
                n++
            } catch (e: Exception) {
                log("skip $rel: ${e.message}")
            }
            if (i % 10 == 0) log("upload $i/${files.size}")
        }
        return n
    }

    suspend fun downloadDir(backend: StorageBackend, prefix: String, localDir: File, log: (String) -> Unit): Int {
        var n = 0
        suspend fun pull(remoteDir: String, local: File) {
            val entries = try {
                backend.list(remoteDir)
            } catch (e: Exception) {
                return
            }
            entries.forEach { name ->
                val remote = if (remoteDir.isEmpty()) name else "$remoteDir/$name"
                val dst = File(local, name)
                val kids = try {
                    backend.list(remote)
                } catch (e: Exception) {
                    null
                }
                if (kids != null && kids.isNotEmpty()) {
                    dst.mkdirs()
                    pull(remote, dst)
                } else if (kids != null) {
                    dst.mkdirs()
                } else {
                    try {
                        backend.download(remote, dst)
                        n++
                    } catch (e: Exception) {
                        log("skip $remote: ${e.message}")
                    }
                }
            }
        }
        pull(prefix.trim('/'), localDir)
        return n
    }
}
