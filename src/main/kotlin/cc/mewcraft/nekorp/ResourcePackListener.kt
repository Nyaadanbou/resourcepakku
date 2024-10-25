package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.config.*
import cc.mewcraft.nekorp.pack.NekoRpManager
import com.google.common.collect.Table
import com.google.common.collect.Tables
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent
import com.velocitypowered.api.event.player.ServerPreConnectEvent
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent
import net.kyori.adventure.resource.ResourcePackRequest
import org.slf4j.Logger
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class ResourcePackListener(
    plugin: NekoRp,
) {
    private val config: NekoRpConfig = plugin.config
    private val nekoRpManager: NekoRpManager = plugin.nekoRpManager
    private val logger: Logger = plugin.logger

    /**
     * row key: player unique id
     * column key: pack name unique id
     * value: a future that will be completed when the player has finished downloading the pack
     */
    private val playerPackStatus: Table<UUID, UUID, CompletableFuture<ResourcePackResult>> = Tables.newCustomTable(ConcurrentHashMap()) { ConcurrentHashMap() }

    /**
     * Represents the changes that need to be applied to a player's resource packs when they are in configuration.w
     */
    private val playerChanges: ConcurrentHashMap<UUID, List<ResourcePackSender.Change>> = ConcurrentHashMap()

    @Subscribe
    private fun onServerPreConnect(event: ServerPreConnectEvent) {
        val player = event.player
        val originalServer = event.originalServer.serverInfo.name

        //<editor-fold desc="Packs">
        val currentServerConfigs = config.getPackConfigs(originalServer)
        if (currentServerConfigs.isEmpty()) {
            logger.info("No resource packs are configured for server {}", originalServer)
            player.clearResourcePacks()
            return
        }
        //</editor-fold>
        val playerAppliedPacks = player.appliedResourcePacks
            .flatMap { it.asResourcePackRequest().packs() }
            .map { config.getPackConfig(it.id()) ?: MinecraftPackConfig(it) }
            .let { PackConfigs.of(it) }

        val changes = ResourcePackSender(currentServerConfigs, playerAppliedPacks).getChanges()
        logger.info("Player {} has {} changes to their resource packs. Changes: {}", player.uniqueId, changes.size, changes)

        if (changes.isNotEmpty()) {
            playerChanges[player.uniqueId] = changes
        }
    }

    @Subscribe(order = PostOrder.LAST)
    private fun onResourcePackStatus(event: PlayerResourcePackStatusEvent) {
        val status = event.status
        if (!status.isIntermediate) {
            val player = event.player
            val playerUniqueId = player.uniqueId
            val packUuid = event.packId ?: return
            logger.info("Player {} has {} the resource pack {}", playerUniqueId, status, packUuid)
            val pluginPackByNameUUID = config.getPackConfig(packUuid)

            playerPackStatus.remove(playerUniqueId, packUuid)
                ?.complete(ResourcePackResult(pluginPackByNameUUID, status == PlayerResourcePackStatusEvent.Status.SUCCESSFUL))
        }
    }

    @Subscribe
    private fun onPlayerDisconnect(event: DisconnectEvent) {
        val player = event.player
        val playerUniqueId = player.uniqueId
        playerChanges.remove(playerUniqueId)
        val packMap = playerPackStatus.rowMap().remove(playerUniqueId) ?: return
        for ((_, future) in packMap) {
            future.complete(ResourcePackResult(EmptyPackConfig, false))
        }
    }

    @Subscribe
    private fun onConfiguration(event: PlayerConfigurationEvent): EventTask? {
        val player = event.player
        val playerUniqueId = player.uniqueId
        val changes = playerChanges.remove(playerUniqueId)
            ?: return null

        val address = player.remoteAddress.address

        //<editor-fold desc="Create future">
        val future = CompletableFuture<ResourcePackResult>()
        future.whenComplete { result, _ ->
            if (result.success) {
                logger.info("Player {} has successfully downloaded the resource pack", player.uniqueId)
            } else {
                val pack = result.pack
                if (pack != null) {
                    // 证明是插件的资源包, 进行失败处理
                    nekoRpManager.onFailedDownload(playerUniqueId, address, pack)
                }
                logger.info("Player {} has failed to download the resource pack", player.uniqueId)
            }
        }
        //</editor-fold>

        // Create a request template
        val request = ResourcePackRequest.resourcePackRequest()
            .prompt(config.prompt)
            .required(config.force)
            .replace(true)

        for (change in changes) {
            change.apply(player, request)
            if (change.type == ResourcePackSender.Change.Type.REMOVE) {
                continue
            }
            playerPackStatus.put(player.uniqueId, change.pack.uniqueId, future)
        }
        val futures = playerPackStatus.row(playerUniqueId).values

        return EventTask.resumeWhenComplete(CompletableFuture.allOf(*futures.toTypedArray()))
    }
}

private data class ResourcePackResult(
    val pack: PackConfig?,
    val success: Boolean,
)