@file:Suppress("UnstableApiUsage")

package cc.mewcraft.nekorp.pack

import cc.mewcraft.nekorp.OSSRequester
import cc.mewcraft.nekorp.config.EmptyPackConfig
import cc.mewcraft.nekorp.config.NekoRpConfig
import cc.mewcraft.nekorp.config.OSSPackConfig
import cc.mewcraft.nekorp.config.PackConfig
import cc.mewcraft.nekorp.plugin
import com.aliyun.oss.HttpMethod
import com.aliyun.oss.OSSException
import com.aliyun.oss.model.GeneratePresignedUrlRequest
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalCause
import com.google.common.hash.HashCode
import org.slf4j.Logger
import java.net.InetAddress
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class NekoRpManager(
    private val logger: Logger,
    config: NekoRpConfig,
) {
    private val requester: OSSRequester = plugin.ossRequester
    private val expireSeconds: Long = config.expireSeconds
    private val limitSeconds: Long = config.limitSeconds

    /**
     * 用于存储玩家的资源包下载地址。
     *
     * 当玩家请求资源包下载地址时，将会生成一个下载地址，并且存储在这个缓存中。
     * 此后的请求将会直接返回这个地址 (尽管链接已经过期），直到缓存过期。
     * 以此来限制玩家频繁请求资源包下载地址。
     */
    private val packDataLimitCache: LoadingCache<PackDataKey, PackData> = Caffeine.newBuilder()
        .expireAfterWrite(limitSeconds, TimeUnit.SECONDS)
        .removalListener<PackDataKey, PackData> { key, _, cause ->
            if (cause == RemovalCause.EXPIRED) {
                logger.info("Removed expired pack data for player uuid ${key?.uuid}. Pack: ${key?.packConfig}")
            }
        }
        .build { key ->
            getPackDownloadAddress(key.packConfig).also {
                logger.info("Successfully generated download link ${it.downloadUrl} for ${key.uuid}. IP: ${key.inetAddress}. Pack: ${key.packConfig}")
            }
        }
    private val packLastModifiedCache: MutableMap<PackConfig, Date> = ConcurrentHashMap()

    private val packHashCodeCache: MutableMap<PackConfig, HashCode> = ConcurrentHashMap()

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

    fun getComputedPackHash(packConfig: PackConfig): HashCode? {
        return packHashCodeCache[packConfig]
    }

    fun putComputedPackHash(packConfig: PackConfig, hash: HashCode) {
        packHashCodeCache[packConfig] = hash
    }

    /**
     * 当资源包下载失败时调用。
     *
     * 将会移除现存的限制。
     *
     * @param uniqueId 玩家的 UUID。
     */
    fun onFailedDownload(uniqueId: UUID, playerIp: InetAddress, packConfig: PackConfig) {
        if (packConfig is EmptyPackConfig)
            return
        packDataLimitCache.invalidate(PackDataKey(uniqueId, playerIp, packConfig))
    }

    /**
     * 获取资源包的下载地址。
     *
     * @param packConfig 资源包配置。
     * @throws OSSException 如果无法获取资源包下载地址。
     */
    private fun getPackDownloadAddress(packConfig: PackConfig): PackData {
        if (packConfig !is OSSPackConfig) {
            throw IllegalArgumentException("Pack config is not an OSS pack config")
        }
        val bucketName = packConfig.bucketName
        return requester.useClient {
            val objectName = packConfig.packPath.toString()
            val objectMetadata = getObjectMetadata(bucketName, objectName)
            if (objectMetadata.contentType != "application/zip") {
                throw IllegalStateException("Pack file is not a zip file")
            }
            // If the pack file has been modified, update the cache
            val lastModified = objectMetadata.lastModified
            if (packLastModifiedCache[packConfig] != lastModified) {
                packLastModifiedCache[packConfig] = lastModified
                packHashCodeCache -= packConfig
            }

            val request = GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.GET).apply {
                // Set the expiration time of the URL to 30 minutes from now
                expiration = Date.from(Instant.now().plusSeconds(expireSeconds))
            }
            val generatedLink = generatePresignedUrl(request)
            PackData(generatedLink)
        }
    }

    fun onDisable() {
        packDataLimitCache.invalidateAll()
        packHashCodeCache.clear()
        requester.close()
    }
}