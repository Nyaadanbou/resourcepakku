package cc.mewcraft.resourcepakku.manager

import cc.mewcraft.resourcepakku.logger
import cc.mewcraft.resourcepakku.model.*
import cc.mewcraft.resourcepakku.plugin
import cc.mewcraft.resourcepakku.server
import cc.mewcraft.resourcepakku.util.ResourcePackChanges
import com.google.common.collect.Table
import com.google.common.collect.Tables
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent
import com.velocitypowered.api.proxy.player.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * 包含了分发资源包的逻辑.
 *
 * @author Akiranya
 */
class ResourcePackController(
    /**
     * 阿里云OSS分发服务.
     */
    private val aliyunOssDistService: AliyunOssDistService,
    /**
     * 内置HTTP分发服务.
     */
    private val selfHostingDistService: SelfHostingDistService,
    /**
     * - key: pack id
     * - val: pack info
     *
     * 已加载的资源包信息映射.
     */
    private val packInfoById: ConcurrentHashMap<UUID, PackInfo>,
    /**
     * - key: pack name
     * - val: pack info
     *
     * 已加载的资源包信息映射.
     */
    private val packInfoByName: ConcurrentHashMap<String, PackInfo>,
    /**
     * 默认的资源包请求信息.
     */
    private val defaultPackRequest: PackRequest,
    /**
     * - key: server name
     * - val: pack request
     *
     * 各个服务器对应的资源包请求映射.
     */
    private val serverPackRequestMap: ConcurrentHashMap<String, PackRequest>,
) {
    /**
     * - key: player id
     * - val: pack changes
     *
     * Represents the resource pack changes that need to be applied to a player when they enter configuration phase.
     */
    private val queuedPackChanges: ConcurrentHashMap<UUID, ResourcePackChanges> = ConcurrentHashMap()

    /**
     * - row key: player unique id
     * - col key: resource pack unique id
     * - value: a future that will be completed when the player has successfully applied the pack
     */
    private val queuedResumeSignals: Table<UUID, UUID, CompletableFuture<ResumeSignal>> = Tables.newCustomTable(ConcurrentHashMap()) { ConcurrentHashMap() }

    companion object {

        @JvmStatic
        fun fromPluginConfig(
            config: PluginConfig,
        ): ResourcePackController {
            return ResourcePackController(
                aliyunOssDistService = config.aliyunOssDistService,
                selfHostingDistService = config.selfHostingDistService,
                packInfoById = ConcurrentHashMap(config.packInfoById),
                packInfoByName = ConcurrentHashMap(config.packInfoByName),
                defaultPackRequest = config.defaultPackRequest,
                serverPackRequestMap = ConcurrentHashMap(config.serverPackRequestMap),
            )
        }
    }

    /**
     * 启动.
     */
    fun start() {
        aliyunOssDistService.start()
        selfHostingDistService.start()
        server.eventManager.register(plugin, this)
    }

    /**
     * 关闭.
     */
    fun close() {
        server.eventManager.unregisterListener(plugin, this)
        selfHostingDistService.close()
        aliyunOssDistService.close()
    }

    // 玩家连接到一个后端服务器之前, 计算资源包的更改并暂存, 延迟到 configuration phase 应用
    @Subscribe(order = PostOrder.LAST)
    private fun onServerPreConnect(event: ServerPreConnectEvent) {
        if (!event.result.isAllowed) return // 玩家被拒绝连接, 不处理

        val player = event.player
        val targetServerName = event
            .result
            .server
            .get() // 如果玩家被允许连接, 则 get() 一定不为 null
            .serverInfo.name
        val targetServerPackRequest = serverPackRequestMap[targetServerName] ?: defaultPackRequest

        // 计划上需要应用的资源包 (也就是配置文件里写的是什么, 这里就是什么)
        val toApply = targetServerPackRequest.packs
        // 客户端已经应用的资源包
        val applied = player.appliedResourcePacks
            .filter { it.origin == ResourcePackInfo.Origin.PLUGIN_ON_PROXY } // 只关注由 Velocity 管理的资源包
            .map { it.asResourcePackRequest() }
            .flatMap { it.packs() }
            .map { packInfoById[it.id()] ?: PackInfo.external(it) }

        // 计算更改 (最终要添加和移除的资源包)
        val resourcePackChanges = ResourcePackChanges.calculate(preToApply = toApply, preApplied = applied)

        when (resourcePackChanges) {
            is ResourcePackChanges.NoOp -> {
                logger.info("No operations to perform for ${player.username}(${player.uniqueId}) when connecting to $targetServerName")
                return // 提前返回
            }

            is ResourcePackChanges.Clear -> {
                logger.info("Scheduled to clear all packs for ${player.username}({${player.uniqueId}}) when connecting to $targetServerName")
            }

            is ResourcePackChanges.Normal -> {
                logger.info("Scheduled to add/remove packs for ${player.username}(${player.uniqueId}) when connecting to $targetServerName: +[${resourcePackChanges.toAdd.joinToString { it.name.toString() }}], -[${resourcePackChanges.toRemove.joinToString { it.name.toString() }}]")
            }
        }

        // 暂存起来, 等待玩家进入 configuration phase 时应用
        queuedPackChanges[player.uniqueId] = resourcePackChanges
    }

    // 根据玩家发送给服务端的资源包状态事件, complete 对应的 future
    @Subscribe(order = PostOrder.LAST)
    private fun onPlayerResourcePackStatus(event: PlayerResourcePackStatusEvent) {
        val packId = event.packId ?: return
        val packInfo = event.packInfo ?: return
        if (packInfo.origin != ResourcePackInfo.Origin.PLUGIN_ON_PROXY) {
            logger.info("Skipping non-proxy-origin pack status event (pack: ${event.packId.toString()})")
            return
        }

        val packName = packInfoById[packId]?.name ?: "unknown"
        val status = event.status
        val player = event.player
        val playerName = player.username
        logger.info("$playerName responded `$status` to proxy-origin pack $packName($packId)")

        if (!status.isIntermediate) {
            val playerId = player.uniqueId
            val resumeSignalFuture = queuedResumeSignals.remove(playerId, packId)
            if (resumeSignalFuture != null) {
                if (status == PlayerResourcePackStatusEvent.Status.SUCCESSFUL) {
                    resumeSignalFuture.complete(ResumeSignal(packId, status))
                } else {
                    player.disconnect(Component.text("Error applying resource pack"))
                }
            }
        }
    }

    // 如果玩家正在接收资源包, 断开连接则标记玩家没有接受资源包
    @Subscribe
    private fun onDisconnect(event: DisconnectEvent) {
        val player = event.player
        val playerId = player.uniqueId
        queuedPackChanges.remove(playerId)
        queuedResumeSignals.rowMap().remove(playerId)?.forEach { (packId: UUID, signalFuture: CompletableFuture<ResumeSignal>) ->
            signalFuture.complete(ResumeSignal(packId, PlayerResourcePackStatusEvent.Status.DISCARDED))
        }
    }

    // 玩家进入配置阶段时, 应用资源包的更改
    // 该函数 return null = 让 velocity 继续该事件
    @Subscribe
    private fun onPlayerConfiguration(event: PlayerConfigurationEvent): EventTask? {
        val server = event.server
        val serverName = server.serverInfo.name
        val player = event.player
        val playerId = player.uniqueId
        val playerIp = player.remoteAddress.address
        val playerName = player.username
        val resourcePackChanges = queuedPackChanges.remove(playerId) ?: run {
            logger.warn("No queued changes for $playerName($playerId)")
            return null
        }

        when (resourcePackChanges) {
            is ResourcePackChanges.NoOp -> {
                logger.error("NoOp should not be here", IllegalStateException())
                return null
            }

            is ResourcePackChanges.Clear -> {
                logger.info("Clearing all packs for ${player.username}(${player.uniqueId}) when configuring for $serverName")
                // 仅移除由本插件管理的资源包
                val allKnownPackIds = packInfoById.values.map { it.id }
                // 移除资源包只需要指定 id
                player.removeResourcePacks(allKnownPackIds)
                return null
            }

            is ResourcePackChanges.Normal -> {
                logger.info("Applying packs to ${player.username}(${player.uniqueId}) when configuring for $serverName")

                // 移除资源包
                if (resourcePackChanges.toRemove.isNotEmpty()) {
                    val toRemovePackIdList = resourcePackChanges.toRemove.map { it.id }
                    // 移除资源包只需要指定 id
                    player.removeResourcePacks(toRemovePackIdList)
                }

                // 添加资源包
                if (resourcePackChanges.toAdd.isNotEmpty()) {
                    val toAddAdvtPackInfoFutList = resourcePackChanges.toAdd.map { it.generateResourcePackInfo(playerId, playerIp) }
                    // 添加资源包需要完整的 ResourcePackInfo
                    CompletableFuture
                        // 等待所有的 ResourcePackInfo 都生成完毕
                        .allOf(*toAddAdvtPackInfoFutList.toTypedArray())
                        // 生成完毕后, 构建 ResourcePackRequest
                        .thenApply {
                            val internalPackRequest = serverPackRequestMap.get(serverName) ?: defaultPackRequest
                            val adventurePackInfoList = toAddAdvtPackInfoFutList.map { it.resultNow() }
                            val adventurePackRequest = ResourcePackRequest.resourcePackRequest()
                                .packs(adventurePackInfoList)
                                .prompt(internalPackRequest.prompt)
                                .required(internalPackRequest.force)
                                .replace(true)
                                .build()
                            adventurePackRequest
                        }
                        // 将构建的 ResourcePackRequest 发送给玩家
                        .thenAccept {
                            player.sendResourcePacks(it)
                        }

                    // 为每个要添加的资源包创建一个 future, 并存储在 queuedResumeSignals 里
                    // 这些 CompletableFutures 会在玩家向服务器发送资源包状态时被 complete
                    val toAddPackIdList = resourcePackChanges.toAdd.map { it.id }
                    for (packId in toAddPackIdList) {
                        queuedResumeSignals.put(playerId, packId, CompletableFuture())
                    }
                }
                val relevantSignalFutures = queuedResumeSignals.row(playerId).values
                val allOfRelevantSignalFutures = CompletableFuture.allOf(*relevantSignalFutures.toTypedArray())
                return EventTask.resumeWhenComplete(allOfRelevantSignalFutures)
            }
        }
    }
}

private data class ResumeSignal(
    val packId: UUID?,
    val packStatus: PlayerResourcePackStatusEvent.Status,
)
