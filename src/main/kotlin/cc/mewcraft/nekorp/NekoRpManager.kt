@file:Suppress("UnstableApiUsage")

package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.util.plugin
import com.aliyun.oss.HttpMethod
import com.aliyun.oss.OSSException
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
    /**
     * 此文件的 SHA-1 值
     *
     * @return null 代表未提供哈希
     */
    val hash: HashCode?,
)

data class PackDataKey(
    val uuid: UUID,
    val inetAddress: InetAddress,
    val packConfig: PackConfig,
)

class NekoRpManager(
    config: NekoRpConfig,
) {
    private val requester: OSSRequester = plugin.ossRequester
    private val expireSeconds: Long = config.expireSeconds
    private val limitSeconds: Long = config.limitSeconds

    // Row: UUID
    // Col: Player IP
    // Val: Download URL
    private val packDataLimitCache: LoadingCache<PackDataKey, PackData> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(limitSeconds))
        .build { key -> getPackDownloadAddress(key.packConfig) }

    fun getPackData(uniqueId: UUID, playerIp: InetAddress, packConfig: PackConfig): PackData? {
        return try {
            packDataLimitCache[PackDataKey(uniqueId, playerIp, packConfig)]
        } catch (e: OSSException) {
            null
        }
    }

    private fun getPackDownloadAddress(packConfig: PackConfig): PackData {
        val bucketName = packConfig.bucketName
        val generatedLink = requester.useClient {
            val objectName = "${packConfig.packPrefix}${packConfig.packPathName}"
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

        val packHashString = packConfig.packHash
        val hash = runCatching { packHashString?.let { HashCode.fromString(it) } }
            .getOrNull() ?: getHashFromOSS(packHashString, packConfig.packPrefix, bucketName)

        val result = PackData(generatedLink, hash)

        return result
    }

    private fun getHashFromOSS(packHash: String?, packPrefix: String, bucketName: String): HashCode? {
        packHash ?: return null
        return requester.useClient {
            val objectName = "${packPrefix}$packHash"
            val fileObject = getObject(bucketName, objectName)
            if (fileObject.objectMetadata.contentType != "text/plain") {
                throw IllegalStateException("Checksum file is not a text file")
            }

            val objectContent = fileObject.objectContent
            val text = objectContent.bufferedReader().use { it.readText() }
            HashCode.fromString(text)
        }
    }

    fun onDisable() {
        packDataLimitCache.invalidateAll()
        requester.close()
    }
}