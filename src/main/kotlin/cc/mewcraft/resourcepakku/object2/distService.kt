package cc.mewcraft.resourcepakku.object2

import cc.mewcraft.resourcepakku.logger
import cc.mewcraft.resourcepakku.util.VIRTUAL_THREAD_EXECUTOR
import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder
import com.sun.net.httpserver.HttpExchange
import team.unnamed.creative.server.ResourcePackServer
import team.unnamed.creative.server.handler.ResourcePackRequestHandler
import team.unnamed.creative.server.request.ResourcePackDownloadRequest
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.readBytes

/**
 * 代表一个分发资源包文件的服务.
 */
sealed interface DistService {
    /**
     * 是否启用.
     */
    val enabled: Boolean

    /**
     * 启动该服务, 准备好接受请求.
     */
    fun start()

    /**
     * 关闭该服务, 清理占用的资源.
     */
    fun close()
}

/**
 * 使用阿里云OSS实现的资源包分发服务.
 */
class AliyunOssDistService(
    /**
     * 阿里云OSS的 endpoint.
     */
    val endpoint: String,
    /**
     * 阿里云OSS的 access key id.
     */
    val accessKeyId: String,
    /**
     * 阿里云OSS的 access key secret.
     */
    val accessKeySecret: String,
    /**
     * 从阿里云OSS生成的资源包预签名链接的过期时间 (秒).
     */
    val presignedUrlExpireSeconds: Long,
    /**
     * 从阿里云OSS生成新的资源包预签名链接的最小间隔 (秒).
     */
    val newPresignedUrlIntervalSeconds: Long,
) : DistService {
    override val enabled: Boolean = true // 始终启用

    /**
     * 用于跟阿里云OSS交互的客户端实例.
     */
    internal val ossClient: OSS = OSSClientBuilder().build(
        endpoint,
        accessKeyId,
        accessKeySecret
    )

    override fun start() {
        // 没有额外的启动逻辑
        logger.info("Starting aliyun oss service")
    }

    override fun close() {
        logger.info("Stopping aliyun oss service")
        ossClient.shutdown()
    }
}

/**
 * 使用内置HTTP服务器实现的资源包分发服务.
 */
class SelfHostingDistService(
    override val enabled: Boolean,
    /**
     * 发送到客户端的主机名, 即资源包下载地址中的主机名部分.
     *
     * 注意该主机名不是监听地址, 实现上HTTP服务器将运行在通配符地址上.
     */
    val host: String,
    /**
     * 监听的端口号.
     */
    val port: Int,
    /**
     * 仅允许来自 Minecraft 客户端发起的资源包请求.
     */
    val validOnly: Boolean,
    /**
     * 本地文件系统之下的资源包文件的根目录.
     *
     * 收到的HTTP请求会被映射到该目录下的文件.
     */
    val rootPath: Path,
) : DistService {

    internal val webserver: ResourcePackServer = ResourcePackServer.server()
        .address(InetSocketAddress(port)) // 监听在通配符地址上
        .executor(VIRTUAL_THREAD_EXECUTOR)
        .handler(LocalFileSystemRequestHandler())
        .build()

    internal fun getLocalPath(path: Path): Path {
        return rootPath.resolve(path)
    }

    override fun start() {
        if (!enabled) return
        logger.info("Starting webserver service at port $port")
        webserver.start()
    }

    override fun close() {
        if (!enabled) return
        logger.info("Stopping webserver service at port $port")
        webserver.stop(0)
    }

    private inner class LocalFileSystemRequestHandler : ResourcePackRequestHandler {
        override fun onRequest(request: ResourcePackDownloadRequest?, exchange: HttpExchange) {
            if (request == null && validOnly) {
                logger.info("Rejecting invalid request - ${exchange.requestURI} - ${exchange.remoteAddress}")
                val data = "Please use a Minecraft client\n".toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.set("Content-Type", "text/plain")
                exchange.sendResponseHeaders(400, data.size.toLong())
                exchange.responseBody.use { responseStream ->
                    responseStream.write(data)
                }
                return
            }

            // 考虑到生产环境下强烈不推荐使用该实现, 这里就直接从磁盘读取文件, 不进行缓存, 方便调试
            val data = rootPath.resolve(exchange.requestURI.path.removePrefix("/")).readBytes()

            logger.info("Handling valid request - ${exchange.requestURI} - ${exchange.remoteAddress}")
            exchange.responseHeaders.set("Content-Type", "application/zip")
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { responseStream ->
                responseStream.write(data)
            }
        }

        override fun toString(): String {
            return "LocalFileSystemRequestHandler{" +
                    "host=$host," +
                    "port=$port," +
                    "validOnly=$validOnly," +
                    "rootPath=$rootPath" +
                    "}"
        }
    }
}