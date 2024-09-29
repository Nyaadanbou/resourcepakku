package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.command.MainCommand
import cc.mewcraft.nekorp.config.EmptyPackConfigs
import cc.mewcraft.nekorp.config.NekoRpConfig
import cc.mewcraft.nekorp.config.NekoRpConfig.ConfigUtil.toNameUUID
import cc.mewcraft.nekorp.config.PackConfig
import cc.mewcraft.nekorp.event.NekoRpReloadEvent
import cc.mewcraft.nekorp.pack.NekoRpManager
import com.google.common.base.Throwables
import com.google.common.hash.HashCode
import com.google.inject.Inject
import com.velocitypowered.api.event.EventHandler
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackInfoLike
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.resource.ResourcePackRequestLike
import org.jetbrains.annotations.Blocking
import org.slf4j.Logger
import java.io.IOException
import java.net.InetAddress
import java.net.URISyntaxException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletionException
import java.util.concurrent.TimeUnit

@Plugin(
    id = "nekorp",
    name = "NekoRp",
    version = "1.0.0",
    dependencies = [Dependency(id = "kotlin")],
    authors = ["g2213swo"]
)
class NekoRp @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory
    private val dataDirectory: Path,
) {

    companion object {
        internal var INSTANCE: NekoRp? = null
    }

    lateinit var config: NekoRpConfig
    lateinit var ossRequester: OSSRequester
    lateinit var nekoRpManager: NekoRpManager

    @Subscribe
    private fun onProxyInitialization(event: ProxyInitializeEvent) {
        INSTANCE = this
        this.config = NekoRpConfig(dataDirectory)
        config.onReload()
        this.ossRequester = OSSRequester(config)
        this.nekoRpManager = NekoRpManager(logger, config)

        val mainCommand = MainCommand()
        val commandManager = server.commandManager
        commandManager.register(
            commandManager.metaBuilder("nekorp")
                .plugin(this)
                .build(),
            mainCommand
        )
    }

    @Subscribe
    private fun onProxyShutdown(event: ProxyShutdownEvent) {
        INSTANCE = null
        nekoRpManager.onDisable()
    }

    @Subscribe
    private fun onResourcePackStatus(event: PlayerResourcePackStatusEvent) {
        val packInfo = event.packInfo ?: return
        val nameUUID = packInfo.id
        val packByNameUUID = config.getPackConfigFromNameUUID(nameUUID) ?: return

        val player = event.player
        val playerUniqueId = player.uniqueId
        val address = player.remoteAddress.address
        // When the player fails to download the pack, we need to remove the access limit for the player
        when (val status = event.status) {
            PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD, PlayerResourcePackStatusEvent.Status.FAILED_RELOAD, PlayerResourcePackStatusEvent.Status.DECLINED, PlayerResourcePackStatusEvent.Status.INVALID_URL, PlayerResourcePackStatusEvent.Status.DISCARDED -> {
                nekoRpManager.onFailedDownload(playerUniqueId, address, packByNameUUID)
                logger.info("Player {} failed to download pack {}. status: {}", player.username, packByNameUUID.configPackName, status)
            }

            PlayerResourcePackStatusEvent.Status.ACCEPTED -> logger.info("Player {} accepted pack {}", player.username, packByNameUUID.configPackName)
            PlayerResourcePackStatusEvent.Status.DOWNLOADED -> logger.info("Player {} downloaded pack or use client cached pack {}", player.username, packByNameUUID.configPackName)
            PlayerResourcePackStatusEvent.Status.SUCCESSFUL -> logger.info("Player {} successfully applied pack {}", player.username, packByNameUUID.configPackName)
        }
    }

    @Subscribe
    private fun onLogin(event: ServerConnectedEvent) {
        val player = event.player
        val currentServer = event.server.serverInfo.name

        //<editor-fold desc="Packs">
        val currentServerConfigs = config.getServerPackConfigs(currentServer)
        if (currentServerConfigs == EmptyPackConfigs) {
            // No packs for this server
            player.clearResourcePacks()
            return
        }
        //</editor-fold>

        server.scheduler
            .buildTask(this, Runnable {
                // Get the packs that need to be applied
                val applyPacks = currentServerConfigs.mapNotNull { getResourcePackInfo(player.uniqueId, player.remoteAddress.address, it) }

                // Create the request
                val request: ResourcePackRequestLike = ResourcePackRequest.resourcePackRequest() // Reverse the list to apply the packs in the correct order
                    // Velocity applies packs in reverse order
                    .packs(applyPacks.reversed())
                    .prompt(config.prompt)
                    .required(config.force)
                    .replace(true)

                // 清理不由本插件管理的资源包
                player.clearResourcePacks()
                // Send the new request
                player.sendResourcePacks(request)
            })
            .delay(1, TimeUnit.SECONDS)
            .schedule()
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
        val resourcePackId = toNameUUID(packConfig)
        try {
            val hash = nekoRpManager.getComputedPackHash(packConfig)
            val builder = ResourcePackInfo.resourcePackInfo()
                .id(resourcePackId)
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

    fun <T> listen(eventType: Class<T>?, order: PostOrder?, action: EventHandler<T>?) {
        server.eventManager.register(this, eventType, order, action)
    }

    fun reload() {
        server.eventManager.fire(NekoRpReloadEvent())
    }
}

internal val plugin: NekoRp
    get() = NekoRp.INSTANCE ?: throw IllegalStateException("Plugin not initialized")