package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.config.NekoRpConfig
import cc.mewcraft.nekorp.config.PackConfig
import cc.mewcraft.nekorp.pack.NekoRpManager
import com.google.common.base.Throwables
import com.google.common.hash.HashCode
import com.velocitypowered.api.event.Continuation
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackInfoLike
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import org.jetbrains.annotations.Blocking
import org.slf4j.Logger
import java.io.IOException
import java.net.InetAddress
import java.net.URISyntaxException
import java.util.*
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

class ResourcePackListener(
    plugin: NekoRp,
) {
    private val config: NekoRpConfig = plugin.config
    private val nekoRpManager: NekoRpManager = plugin.nekoRpManager
    private val logger: Logger = plugin.logger

    private val playerContinuations = ConcurrentHashMap<UUID, Continuation>()

    @Subscribe
    private fun onResourcePackStatus(event: PlayerResourcePackStatusEvent) {
        val player = event.player
        val playerUniqueId = player.uniqueId
        val packUuid = event.packId
        val packByNameUUID = packUuid?.let { config.getPackConfigFromNameUUID(it) }
        if (packByNameUUID == null) {
            logger.error("Failed to find pack configuration for pack {}", packUuid)
            val continuation = playerContinuations.remove(playerUniqueId)
            continuation?.resume()
            return
        }

        val address = player.remoteAddress.address
        // When the player fails to download the pack, we need to remove the access limit for the player
        when (val status = event.status) {
            PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD, PlayerResourcePackStatusEvent.Status.FAILED_RELOAD, PlayerResourcePackStatusEvent.Status.INVALID_URL, PlayerResourcePackStatusEvent.Status.DISCARDED -> {
                nekoRpManager.onFailedDownload(playerUniqueId, address, packByNameUUID)
                val continuation = playerContinuations.remove(playerUniqueId)
                continuation?.resume()
                logger.info("Player {} failed to download pack {}. status: {}", player.username, packByNameUUID, status)
            }

            PlayerResourcePackStatusEvent.Status.DECLINED -> {
                nekoRpManager.onFailedDownload(playerUniqueId, address, packByNameUUID)
                val continuation = playerContinuations.remove(playerUniqueId)
                continuation?.resume()
                logger.info("Player {} declined pack {}", player.username, packByNameUUID)
            }

            PlayerResourcePackStatusEvent.Status.ACCEPTED -> logger.info("Player {} accepted pack {}", player.username, packByNameUUID)
            PlayerResourcePackStatusEvent.Status.DOWNLOADED -> logger.info("Player {} downloaded pack or use client cached pack {}", player.username, packByNameUUID)
            PlayerResourcePackStatusEvent.Status.SUCCESSFUL -> {
                val continuation = playerContinuations.remove(playerUniqueId)
                continuation?.resume()
                logger.info("Player {} successfully applied pack {}", player.username, packByNameUUID)
            }
        }
    }

    @Subscribe
    private fun onConfiguration(event: PlayerConfigurationEvent, continuation: Continuation) {
        val player = event.player
        playerContinuations[player.uniqueId] = continuation
        val currentServer = event.server.serverInfo.name

        //<editor-fold desc="Packs">
        val currentServerConfigs = config.getServerPackConfigs(currentServer)
        //</editor-fold>

        // Get the packs that need to be applied
        val applyPacks = currentServerConfigs.mapNotNull { getResourcePackInfo(player.uniqueId, player.remoteAddress.address, it) }

        // Create the request
        val request = ResourcePackRequest.resourcePackRequest()
            // Velocity applies packs in reverse order
            .prompt(config.prompt)
            .required(config.force)
            .replace(true)

        val changes = ResourcePackSender(applyPacks, player.appliedResourcePacks.flatMap { it.asResourcePackRequest().packs() }).getChanges()
        for (change in changes) {
            change.apply(player, request)
        }
    }

    /**
     * Get the ResourcePackInfo for a pack.
     *
     * @param playerUniqueId The unique id of the player.
     * @param address        The address of the player.
     * @param packConfig     The pack configuration.
     *
     * @return The ResourcePackInfo or null if an error occurred.
     */
    @Blocking
    private fun getResourcePackInfo(playerUniqueId: UUID, address: InetAddress, packConfig: PackConfig): ResourcePackInfoLike? {
        val result = nekoRpManager.getPackData(playerUniqueId, address, packConfig)
        if (result == null) {
            logger.error("Failed to get pack data, please check your configuration. Pack: {}", packConfig.configPackName)
            return null
        }
        try {
            val hash = nekoRpManager.getComputedPackHash(packConfig)
            val nameUniqueId = packConfig.nameUniqueId
            logger.info("Sending pack {} to player {}, pack name unique id: {}", packConfig.configPackName, playerUniqueId, nameUniqueId)
            val builder = ResourcePackInfo.resourcePackInfo()
                .id(nameUniqueId)
                .uri(result.downloadUrl.toURI())
            if (hash != null) {
                // If the hash is already known, we can set it directly
                return builder.hash(hash.toString()).build()
            }
            // If the hash is not known, we need to compute it and set it in the config
            val info: ResourcePackInfo
            try {
                info = builder.computeHashAndBuild().join()
            } catch (e: CompletionException) {
                // If the error is not an IOException, log it
                // IOExceptions are expected when the player's limit is reached
                if (Throwables.getRootCause(e) !is IOException) {
                    logger.error("Failed to compute hash", e)
                }
                logger.info("Blocked downloading request {} from player {}", packConfig.configPackName, playerUniqueId)
                return null
            }
            nekoRpManager.putComputedPackHash(packConfig, HashCode.fromString(info.hash()))
            return info
        } catch (e: URISyntaxException) {
            logger.error("Failed to parse URI", e)
        }
        return null
    }
}