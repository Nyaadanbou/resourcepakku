package cc.mewcraft.resourcepakku.model

import cc.mewcraft.resourcepakku.logger
import cc.mewcraft.resourcepakku.util.VIRTUAL_THREAD_EXECUTOR
import cc.mewcraft.resourcepakku.util.computeHash
import com.aliyun.oss.HttpMethod
import com.aliyun.oss.model.GeneratePresignedUrlRequest
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalCause
import net.kyori.adventure.resource.ResourcePackInfo
import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 代表一个可以发送给玩家的资源包信息, 类似 Adventure 中的 [ResourcePackInfo].
 *
 * @see PackRequest
 */
sealed interface PackInfo {

    companion object {

        @JvmStatic
        fun external(adventurePackInfo: ResourcePackInfo): ExternalPackInfo {
            return ExternalPackInfo(adventurePackInfo)
        }

        @JvmStatic
        fun aliyunOss(
            id: UUID,
            name: String,
            path: Path,
            bucketName: String,
            aliyunDist: AliyunOssDistService,
        ): AliyunOssPackInfo {
            return AliyunOssPackInfo(id, name, path, bucketName, aliyunDist)
        }

        @JvmStatic
        fun selfHosting(
            id: UUID,
            name: String,
            path: Path,
            selfHostingDist: SelfHostingDistService,
        ): SelfHostingPackInfo {
            return SelfHostingPackInfo(id, name, path, selfHostingDist)
        }
    }

    /**
     * 所属类型.
     */
    val type: Type

    /**
     * 返回资源包信息的 [UUID].
     *
     * 其作用同 [ResourcePackInfo.id].
     */
    val id: UUID

    /**
     * 返回资源包信息的名称. 由配置文件定义.
     */
    val name: String?

    /**
     * 返回资源包信息的相对文件路径. 具体定义参考实现.
     */
    val path: Path

    /**
     * 根据传入的参数生成资源包信息的 [URI].
     * 该 URI 会被发送到客户端以供其下载资源包.
     *
     * 其作用同 [ResourcePackInfo.uri].
     *
     * @param playerId 玩家唯一ID
     * @param playerIp 玩家IP地址
     * @return 生成的 [URI]
     */
    fun generateUri(playerId: UUID, playerIp: InetAddress): CompletableFuture<URI>

    /**
     * 根据传入的参数生成 [ResourcePackInfo].
     *
     * @param playerId 玩家唯一ID
     * @param playerIp 玩家IP地址
     * @return 生成的 [ResourcePackInfo]
     */
    fun generateResourcePackInfo(playerId: UUID, playerIp: InetAddress): CompletableFuture<ResourcePackInfo>

    /**
     * [PackInfo] 的类型.
     */
    enum class Type {
        /**
         * 其他.
         */
        EXTERNAL,
        /**
         * 阿里云OSS.
         */
        ALIYUN_OSS,
        /**
         * 内置 HTTP 服务器.
         */
        SELF_HOSTING,
    }
}

/**
 * 封装了不是由本插件分发的资源包信息.
 */
class ExternalPackInfo(
    private val adventurePackInfo: ResourcePackInfo,
) : PackInfo {
    override val type: PackInfo.Type
        get() = PackInfo.Type.EXTERNAL
    override val id: UUID
        get() = adventurePackInfo.id()
    override val name: String?
        get() = null
    override val path: Path
        get() = throw UnsupportedOperationException()

    override fun generateUri(playerId: UUID, playerIp: InetAddress): CompletableFuture<URI> {
        return CompletableFuture.completedFuture(adventurePackInfo.uri())
    }

    override fun generateResourcePackInfo(playerId: UUID, playerIp: InetAddress): CompletableFuture<ResourcePackInfo> {
        return CompletableFuture.completedFuture(adventurePackInfo)
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true
        if (other !is ExternalPackInfo)
            return false

        return id == other.id
    }
}

/**
 * 由阿里云OSS进行分发的资源包信息.
 */
class AliyunOssPackInfo(
    override val id: UUID,
    override val name: String,
    /**
     * 相对路径.
     *
     * 资源包文件相对于阿里云OSS Bucket 根目录的文件路径, 形如 "path/to/pack.zip".
     */
    override val path: Path,
    /**
     * 阿里云OSS Bucket 的名字.
     */
    val bucketName: String,
    /**
     * 阿里云OSS的资源包分发实例.
     */
    val distService: AliyunOssDistService,
) : PackInfo {

    companion object {
        // TODO 把 cache 移动到 companion object 里?
    }

    init {
        require(!(path.isAbsolute)) { "path should not be absolute" }
    }

    /**
     * key: pack id
     * val: last modified time
     */
    private val packLastModifiedRecords: MutableMap<UUID, Date> = ConcurrentHashMap()

    /**
     * key: request key
     * val: future of presigned URI
     *
     * 存储每个玩家的资源包下载地址.
     *
     * 当玩家请求资源包下载地址时, 程序会向 OSS 发送请求生成一个预签名下载地址, 并且存储在这个缓存中.
     * 此后的请求应该直接返回这个地址 (尽管链接已经过期), 直到缓存过期.
     * 这样的设计是为了防止玩家通过游戏客户端以外的方式直接下载资源包.
     */
    private val adventurePackInfoCache: LoadingCache<RequestKey, CompletableFuture<ResourcePackInfo>> = Caffeine.newBuilder()
        .executor(VIRTUAL_THREAD_EXECUTOR)
        .expireAfterWrite(distService.newPresignedUrlIntervalSeconds, TimeUnit.SECONDS)
        .removalListener<RequestKey, CompletableFuture<ResourcePackInfo>> { key, _, cause ->
            if (cause == RemovalCause.EXPIRED) {
                logger.info("ResourcePackInfo expired for $key")
            }
        }
        .build { key ->
            generateUri(key.playerId, key.playerIp)
                .thenCompose { uri ->
                    ResourcePackInfo.resourcePackInfo()
                        .id(id)
                        .uri(uri)
                        .computeHashAndBuild(VIRTUAL_THREAD_EXECUTOR)
                }
                .whenComplete { info, ex ->
                    if (ex != null) {
                        logger.warn("Error generating ResourcePackInfo for $key", ex)
                        throw ex // rethrow
                    }
                    logger.info("Successfully generated ResourcePackInfo $info for $key")
                }
        }

    override val type: PackInfo.Type = PackInfo.Type.ALIYUN_OSS

    // 形如 https://resrcpacks.oss-cn-zhangjiakou.aliyuncs.com/path/to/pack.zip?Expires=999&OSSAccessKeyId=TMP.someKeyId&Signature=someSignature
    override fun generateUri(playerId: UUID, playerIp: InetAddress): CompletableFuture<URI> {
        val future = CompletableFuture<URI>()
        VIRTUAL_THREAD_EXECUTOR.execute {
            val ossClient = distService.ossClient
            val filePathString = path.toString()

            val objectMetadata = try {
                ossClient.getObjectMetadata(bucketName, filePathString)
            } catch (e: Exception) {
                future.completeExceptionally(e)
                return@execute
            }

            if (objectMetadata.contentType != "application/zip") {
                future.completeExceptionally(IllegalStateException("Pack file is not a zip file"))
                return@execute
            }

            // If the pack file has been modified, update the cache
            val lastModified = objectMetadata.lastModified
            if (packLastModifiedRecords[id] != lastModified) {
                packLastModifiedRecords[id] = lastModified
            }

            // Generate a presigned URL for the pack file
            val request = GeneratePresignedUrlRequest(bucketName, filePathString, HttpMethod.GET).apply {
                // Set the expiration time of the presigned URL
                expiration = Date.from(Instant.now().plusSeconds(distService.presignedUrlExpireSeconds))
            }

            val url = try {
                ossClient.generatePresignedUrl(request)
            } catch (e: Exception) {
                future.completeExceptionally(e)
                return@execute
            }

            val uri = try {
                url.toURI()
            } catch (e: URISyntaxException) {
                future.completeExceptionally(e)
                return@execute
            }

            future.complete(uri)
        }
        return future
    }

    override fun generateResourcePackInfo(playerId: UUID, playerIp: InetAddress): CompletableFuture<ResourcePackInfo> {
        return adventurePackInfoCache.get(RequestKey(playerId, playerIp, id))
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true
        if (other !is AliyunOssPackInfo)
            return false

        return id == other.id
    }

    /**
     * 用于缓存资源包请求的键.
     */
    private data class RequestKey(
        val playerId: UUID,
        val playerIp: InetAddress,
        val packId: UUID,
    ) {
        override fun toString(): String {
            return "RequestKey(playerId=$playerId, playerIp=${playerIp.hostAddress}, packId=$packId)"
        }
    }
}

/**
 * 由内置HTTP服务器进行分发的资源包信息.
 */
class SelfHostingPackInfo(
    override val id: UUID,
    override val name: String,
    /**
     * 相对路径.
     *
     * 资源包文件相对于 HTTP 服务器根目录的路径, 形如 "path/to/pack.zip".
     */
    override val path: Path,
    /**
     * 内置HTTP服务器的资源包分发实例.
     */
    val distService: SelfHostingDistService,
) : PackInfo {

    init {
        require(!(path.isAbsolute)) { "path should not be absolute" }
    }

    override val type: PackInfo.Type = PackInfo.Type.SELF_HOSTING

    // 形如 http://file.example.com/path/to/pack.zip
    override fun generateUri(playerId: UUID, playerIp: InetAddress): CompletableFuture<URI> {
        val host = distService.host
        val port = distService.port
        @Suppress("HttpUrlsUsage")
        val uri = URI.create("http://${host}:${port}/${path}")
        return CompletableFuture.completedFuture(uri)
    }

    // 注意这个实现没有对读取的文件进行任何缓存, 是专门为了方便调试
    override fun generateResourcePackInfo(playerId: UUID, playerIp: InetAddress): CompletableFuture<ResourcePackInfo> {
        val future = generateUri(playerId, playerIp).thenApply { uri ->
            // 创建一个 ResourcePackInfo.Builder (尚未计算哈希, 稍后实时计算)
            val builder = ResourcePackInfo.resourcePackInfo()
                .id(id)
                .uri(uri) // 注意这里的 URI 是发送给客户端的, 我们不能用这个 URI 来计算哈希, 而是要用本地文件系统的路径

            builder
        }.thenCompose { builder ->
            // 获取资源包在本地文件系统上的路径
            val packLocalPath = distService.getLocalPath(path)

            // 直接计算本地文件系统上的资源包哈希
            val future = computeHash(packLocalPath.toUri(), VIRTUAL_THREAD_EXECUTOR).thenApply { hash ->
                builder.hash(hash).build()
            }

            future
        }

        return future
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true
        if (other !is SelfHostingPackInfo)
            return false

        return id == other.id
    }
}