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
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent.Status as PackStatus

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
     * - row key: player id
     * - col key: pack id
     * - value: the target status that the player must respond with to resume the corresponding future
     */
    private val queuedTargetStatus: Table<UUID, UUID, PackStatus> = Tables.newCustomTable(ConcurrentHashMap()) { ConcurrentHashMap() }

    /**
     * - row key: player id
     * - col key: pack id
     * - value: a future that will be completed when the player has successfully applied the pack
     */
    private val queuedResumeSignals: Table<UUID, UUID, CompletableFuture<Unit>> = Tables.newCustomTable(ConcurrentHashMap()) { ConcurrentHashMap() }

    companion object {

        @JvmField
        val ERR_PACK_MESSAGE: Component = Component.text("Error applying resource pack")

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

    /**
     * 重新向客户端发送资源包.
     */
    fun resend(): CompletableFuture<Void> {
        val futures = mutableListOf<CompletableFuture<Void>>()

        for (player in server.allPlayers) {
            val playerName = player.username
            val playerId = player.uniqueId

            logger.info("Clearing all packs for $playerName($playerId)")

            // 仅移除由本插件管理的资源包
            val allKnownPackIds = packInfoById.values.map { it.id }
            // 移除资源包只需要指定 id
            player.removeResourcePacks(allKnownPackIds)

            val targetServerName = player.currentServer.getOrNull()?.server?.serverInfo?.name ?: continue
            val targetServerPackRequest = serverPackRequestMap[targetServerName] ?: defaultPackRequest

            // 计划上需要应用的资源包 (也就是配置文件里写的是什么, 这里就是什么)
            val toApply = targetServerPackRequest.packs

            // 计算得到 ResourcePackInfo 对象以切实发送给客户端
            val finalToAddPackInfoFutures = toApply.map { packInfo ->
                packInfo.generateResourcePackInfo(playerId, player.remoteAddress.address)
            }

            val future = CompletableFuture
                // 等待所有需要添加的 ResourcePackInfo 都生成完毕
                .allOf(*finalToAddPackInfoFutures.toTypedArray())
                // 生成完毕后, 构建 ResourcePackRequest
                .thenApply {
                    val internalPackRequest = serverPackRequestMap.get(targetServerName) ?: defaultPackRequest
                    val adventurePackInfoList = finalToAddPackInfoFutures.map { it.resultNow() }
                    val adventurePackRequest = ResourcePackRequest.resourcePackRequest()
                        .packs(adventurePackInfoList)
                        .prompt(internalPackRequest.prompt)
                        .required(internalPackRequest.force)
                        .replace(true)
                        .build()
                    adventurePackRequest
                }
                // 将构建的 ResourcePackRequest 发送给玩家
                // 与此同时, 移除不需要的资源包
                .thenAccept { request ->
                    logger.info("Resending packs to ${playerName}: [${toApply.joinToString { info -> info.name.toString() }}]")
                    player.sendResourcePacks(request)
                }
                .exceptionally { ex ->
                    logger.error("Failed to apply resource pack changes to $playerName", ex)
                    player.disconnect(ERR_PACK_MESSAGE)
                    null
                }

            futures += future
        }

        return CompletableFuture.allOf(*futures.toTypedArray())
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
                logger.info("Scheduled to add/remove packs for ${player.username}(${player.uniqueId}) when connecting to $targetServerName: +[${resourcePackChanges.finalToAdd.joinToString { it.name.toString() }}], -[${resourcePackChanges.finalToRemove.joinToString { it.name.toString() }}]")
            }
        }

        // 暂存起来, 等待玩家进入 configuration phase 时应用
        queuedPackChanges[player.uniqueId] = resourcePackChanges
    }

    // 根据玩家发送给服务端的资源包状态事件, complete 对应的 future
    @Subscribe(order = PostOrder.LAST)
    private fun onPlayerResourcePackStatus(event: PlayerResourcePackStatusEvent) {
        val player = event.player
        val playerName = player.username

        // 经实际测试:
        // packId 似乎没有为 null 的时候
        // packInfo 当玩家已经安装了某个资源包 *并且* 服务器先前没有显式的发送 ResourcePackRequest 时为 null
        val packId = event.packId ?: run {
            logger.info("No resource pack has been found for $event")
            return
        }

        val status = event.status
        val packName = packInfoById[packId]?.name ?: "unknown"
        logger.info("$playerName responded to $packName($packId) with status `$status`")

        if (!status.isIntermediate) {
            val playerId = player.uniqueId
            val targetStatus = queuedTargetStatus.remove(playerId, packId)
            if (targetStatus != null) {
                if (status == targetStatus) {
                    val signal = queuedResumeSignals.remove(playerId, packId)
                    if (signal != null) {
                        signal.complete(Unit)
                        logger.info("$playerName resumed signal for $packName($packId) with status `$status`")
                    } else {
                        logger.error("No signal found for $playerName and pack $packName($packId)")
                    }
                } else {
                    logger.warn("Unexpected status `$status` from $playerName for pack $packName($packId), expected `$targetStatus`")
                    player.disconnect(ERR_PACK_MESSAGE)
                }
            } else {
                // 我们似乎可以直接忽略掉为 null 的情况 (有问题再说?)
            }
        }
    }

    // 玩家断开连接时, 清理所有与该玩家相关的暂存数据
    @Subscribe
    private fun onDisconnect(event: DisconnectEvent) {
        val player = event.player
        val playerId = player.uniqueId
        queuedPackChanges.remove(playerId)
        queuedTargetStatus.rowMap().remove(playerId)
        queuedResumeSignals.rowMap().remove(playerId)?.forEach { (packId: UUID, signal: CompletableFuture<Unit>) ->
            signal.complete(Unit)
        }
    }

    // 玩家进入配置阶段时, 应用资源包的更改
    // 该函数 return null = 让 velocity 继续该事件
    @Subscribe
    private fun onPlayerConfiguration(event: PlayerConfigurationEvent): EventTask? {
        val serverConn = event.server
        val serverName = serverConn.serverInfo.name
        val player = event.player
        val playerId = player.uniqueId
        val playerIp = player.remoteAddress.address
        val playerName = player.username
        val packChanges = queuedPackChanges.remove(playerId) ?: run {
            logger.warn("No queued changes for $playerName")
            return null
        }

        when (packChanges) {
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
                logger.info("Applying pack changes to ${player.username}(${player.uniqueId}) when configuring for $serverName")

                val finalToRemovePackIdList = packChanges.finalToRemove.map { it.id } // 提示: 移除资源包只需要指定 id
                //val toRemovePackInfoFutures = packChanges.finalToRemove.map { it.generateResourcePackInfo(playerId, playerIp) }

                val preToAddPackIdList = packChanges.preToAdd.map { it.id }
                //val finalToAddPackIdList = packChanges.finalToAdd.map { it.id }
                val finalToAddPackInfoFutures = packChanges.finalToAdd.map { it.generateResourcePackInfo(playerId, playerIp) } // 提示: 添加资源包需要完整的 ResourcePackInfo

                // 为每个要添加/移除的资源包创建一个 future, 并存储在 queuedResumeSignals 里
                // 这些 CompletableFutures 会在玩家向服务器发送资源包状态时被 complete
                //finalToRemovePackIdList.forEach { packId ->
                //    queuedTargetStatus.put(playerId, packId, PackStatus.DISCARDED) // 让客户端移除资源包, 并不会触发 PlayerResourcePackStatusEvent, 所以也没有必要等待
                //    queuedResumeSignals.put(playerId, packId, CompletableFuture())
                //}
                preToAddPackIdList.forEach { packId ->
                    queuedTargetStatus.put(playerId, packId, PackStatus.SUCCESSFUL)
                    queuedResumeSignals.put(playerId, packId, CompletableFuture())
                }

                CompletableFuture
                    // 等待所有需要添加的 ResourcePackInfo 都生成完毕
                    .allOf(*finalToAddPackInfoFutures.toTypedArray())
                    // 生成完毕后, 构建 ResourcePackRequest
                    .thenApply { x ->
                        val internalPackRequest = serverPackRequestMap.get(serverName) ?: defaultPackRequest
                        val adventurePackInfoList = finalToAddPackInfoFutures.map { it.resultNow() }
                        val adventurePackRequest = ResourcePackRequest.resourcePackRequest()
                            .packs(adventurePackInfoList)
                            .prompt(internalPackRequest.prompt)
                            .required(internalPackRequest.force)
                            .replace(true)
                            .build()
                        adventurePackRequest
                    }
                    // 将构建的 ResourcePackRequest 发送给玩家
                    // 与此同时, 移除不需要的资源包
                    .thenAccept({ request ->
                        // Velocity 有个 BUG:
                        // 如果在玩家切换服务器时进入 configuration phase 后立马发送资源包, 会直接卡住, 客户端也不会收到资源包提示,
                        // 所以这里延迟 1 秒再向客户端发送资源包请求 (添加/移除)
                        server.scheduler
                            .buildTask(plugin, Runnable {
                                logger.info("Removing packs from $playerName: [${packChanges.finalToRemove.joinToString { info -> info.name.toString() }}]")
                                player.removeResourcePacks(finalToRemovePackIdList)
                                logger.info("Sending packs to ${playerName}: [${packChanges.finalToAdd.joinToString { info -> info.name.toString() }}]")
                                player.sendResourcePacks(request)
                            })
                            .delay(1, TimeUnit.SECONDS)
                            .schedule()
                    })
                    .exceptionally { ex ->
                        logger.error("Failed to apply resource pack changes to $playerName", ex)
                        player.disconnect(ERR_PACK_MESSAGE)
                        null
                    }

                val relevantSignalFutures = queuedResumeSignals.row(playerId).values
                val allOfRelevantSignalFutures = CompletableFuture.allOf(*relevantSignalFutures.toTypedArray())
                return EventTask.resumeWhenComplete(allOfRelevantSignalFutures)
            }
        }
    }
}
