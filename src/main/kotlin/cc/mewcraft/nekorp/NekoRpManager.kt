@file:Suppress("UnstableApiUsage")

package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.util.plugin
import com.aliyun.oss.HttpMethod
import com.aliyun.oss.OSSException
import com.aliyun.oss.model.GeneratePresignedUrlRequest
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalCause
import com.google.common.hash.HashCode
import org.slf4j.Logger
import java.net.InetAddress
import java.net.URL
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

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
    private val logger: Logger,
    config: NekoRpConfig,
) {
    private val requester: OSSRequester = plugin.ossRequester
    private val expireSeconds: Long = config.expireSeconds
    private val limitSeconds: Long = config.limitSeconds

    private val packDataLimitCache: LoadingCache<PackDataKey, PackData> = Caffeine.newBuilder()
        .expireAfterWrite(limitSeconds, TimeUnit.SECONDS)
        .removalListener<PackDataKey, PackData> { key, _, cause ->
            if (cause == RemovalCause.EXPIRED) {
                logger.info("Removed expired pack data for player uuid ${key?.uuid}. Pack: ${key?.packConfig?.configPackName}")
            }
        }
        .build { key ->
            getPackDownloadAddress(key.packConfig).also {
                logger.info("Successfully generated download link ${it.downloadUrl} for ${key.uuid}. IP: ${key.inetAddress}. Pack: ${key.packConfig.configPackName}")
            }
        }

    /**
     * 获取玩家的资源包下载地址。
     *
     * @param uniqueId 玩家的 UUID。
     * @param playerIp 玩家的 IP 地址。
     * @param packConfig 资源包配置。
     *
     * @return 资源包下载地址， 或 null 如果无法获取。
     */
    fun getPackData(uniqueId: UUID, playerIp: InetAddress, packConfig: PackConfig): PackData? {
        return packDataLimitCache[PackDataKey(uniqueId, playerIp, packConfig)]
    }

    /**
     * 当资源包下载失败时调用。
     *
     * 将会移除现存的限制。
     *
     * @param uniqueId 玩家的 UUID。
     */
    fun onFailedDownload(uniqueId: UUID, playerIp: InetAddress, packConfig: PackConfig) {
        packDataLimitCache.invalidate(PackDataKey(uniqueId, playerIp, packConfig))
    }

    /**
     * 获取资源包的下载地址。
     *
     * @param packConfig 资源包配置。
     * @throws OSSException 如果无法获取资源包下载地址。
     */
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
                expiration = Date.from(Instant.now().plusSeconds(expireSeconds))
            }
            generatePresignedUrl(request)
        }
        val hash = packConfig.packHashCode()
        val result = PackData(generatedLink, hash)

        return result
    }

    fun onDisable() {
        packDataLimitCache.invalidateAll()
        requester.close()
    }
}