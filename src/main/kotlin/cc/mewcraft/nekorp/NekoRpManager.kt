@file:Suppress("UnstableApiUsage")

package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.util.plugin
import com.aliyun.oss.HttpMethod
import com.aliyun.oss.model.GeneratePresignedUrlRequest
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.google.common.hash.HashCode
import java.net.InetAddress
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.*

data class PackData(
    val downloadUrl: URL,
    val hash: HashCode,
)

data class PackDataKey(
    val uuid: UUID,
    val inetAddress: InetAddress,
    val serverConfig: ServerConfig
)

class NekoRpManager(
    private val config: NekoRpConfig,
) {
    private val requester: OSSRequester = plugin.ossRequester
    private val expireSeconds: Long = config.expireSeconds

    // Row: UUID
    // Col: Player IP
    // Val: Download URL
    private val packDataCache: LoadingCache<PackDataKey, PackData> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(expireSeconds))
        .build { key -> getPackDownloadAddress(key.serverConfig) }

    private var oldPackData: PackData? = null
        set(value) {
            if (value == null)
                return
            if (field == null) {
                field = value
                return
            }

            if (field!! != value) {
                packDataCache.invalidateAll()
            }
        }

    private val bucketName: String
        get() = config.bucketName

    fun getPackData(uniqueId: UUID, playerIp: InetAddress, serverConfig: ServerConfig): PackData {
        return packDataCache[PackDataKey(uniqueId, playerIp, serverConfig)]
    }

    private fun getPackDownloadAddress(serverConfig: ServerConfig): PackData {
        val generatedLink = requester.useClient {
            val objectName = "${serverConfig.packPrefix}${serverConfig.packName}"
            val objectMetadata = getObjectMetadata(bucketName, objectName)
            if (objectMetadata.contentType != "application/zip") {
                throw IllegalStateException("Pack file is not a zip file")
            }
            val request = GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.GET).apply {
                // Set the expiration time of the URL to 30 minutes from now
                val expire = Instant.now().plus(Duration.ofSeconds(expireSeconds))
                expiration = Date(expire.toEpochMilli())
            }
            generatePresignedUrl(request)
        }

        val hash = requester.useClient {
            val objectName = "${serverConfig.packPrefix}${serverConfig.packHash}"
            val fileObject = getObject(bucketName, objectName)
            if (fileObject.objectMetadata.contentType != "text/plain") {
                throw IllegalStateException("Checksum file is not a text file")
            }

            val objectContent = fileObject.objectContent
            val text = objectContent.bufferedReader().use { it.readText() }
            HashCode.fromString(text)
        }

        val result = PackData(generatedLink, hash)
        oldPackData = result

        return result
    }

    fun onDisable() {
        packDataCache.invalidateAll()
        requester.close()
    }
}