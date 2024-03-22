package cc.mewcraft.nekorp;

import cc.mewcraft.nekorp.command.MainCommand;
import cc.mewcraft.nekorp.config.NekoRpConfig;
import cc.mewcraft.nekorp.config.PackConfig;
import cc.mewcraft.nekorp.event.NekoRpReloadEvent;
import cc.mewcraft.nekorp.pack.NekoRpManager;
import cc.mewcraft.nekorp.pack.PackData;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackInfoLike;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackRequestLike;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Plugin(id = "nekorp", name = "NekoRp", version = "1.0.0", dependencies = {@Dependency(id = "kotlin")}, authors = {"g2213swo"})
public class NekoRp {
    private static NekoRp instance;

    public static NekoRp getInstance() {
        return instance;
    }

    public final ProxyServer server;
    public final Logger logger;
    public final Path dataDirectory;

    public NekoRpManager nekoRpManager;
    public NekoRpConfig config;
    public OSSRequester ossRequester;

    @Inject
    public NekoRp(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        instance = this;
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    private void onProxyInitialization(ProxyInitializeEvent event) {
        this.config = new NekoRpConfig(dataDirectory);
        config.onReload();
        this.ossRequester = new OSSRequester(config);
        this.nekoRpManager = new NekoRpManager(logger, config);

        var mainCommand = new MainCommand();
        server.getCommandManager().register("nekorp", mainCommand);
    }

    @Subscribe
    private void onProxyShutdown(ProxyShutdownEvent event) {
        nekoRpManager.onDisable();
    }

    @Subscribe
    private void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        var packInfo = event.getPackInfo();
        if (packInfo == null)
            return;
        byte[] hashBytes = packInfo.getHash();
        if (hashBytes == null)
            return;
        HashCode hash = HashCode.fromBytes(hashBytes);
        PackConfig packByHash = config.getPackByHash(hash);
        if (packByHash == null)
            return;

        Player player = event.getPlayer();
        UUID playerUniqueId = player.getUniqueId();
        InetAddress address = player.getRemoteAddress().getAddress();
        // When the player fails to download the pack, we need to remove the access limit for the player
        switch (status) {
            case FAILED_DOWNLOAD, FAILED_RELOAD, DECLINED, INVALID_URL, DISCARDED -> {
                nekoRpManager.onFailedDownload(playerUniqueId, address, packByHash);
                logger.info("Player {} failed to download pack {}. status: {}", player.getUsername(), packByHash.getConfigPackName(), status);
            }
            case ACCEPTED ->
                    logger.info("Player {} accepted pack {}", player.getUsername(), packByHash.getConfigPackName());
            case DOWNLOADED ->
                    logger.info("Player {} downloaded pack {}", player.getUsername(), packByHash.getConfigPackName());
            case SUCCESSFUL ->
                    logger.info("Player {} successfully applied pack {}", player.getUsername(), packByHash.getConfigPackName());
        }
    }

    @Subscribe
    private void onLogin(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String currentServer = event.getServer().getServerInfo().getName();

        //<editor-fold desc="Packs">
        List<PackConfig> currentServerConfigs = config.getServerPacks(currentServer);
        if (currentServerConfigs.isEmpty()) {
            // No packs for this server
            player.clearResourcePacks();
            return;
        }
        //</editor-fold>

        server.getScheduler()
                .buildTask(this, () -> {
                    // Get the packs that need to be applied
                    List<ResourcePackInfoLike> applyPacks = currentServerConfigs.stream()
                            .map(packConfig -> getResourcePackInfo(player.getUniqueId(), player.getRemoteAddress().getAddress(), packConfig))
                            .filter(Objects::nonNull)
                            .toList();

                    // Create the request
                    ResourcePackRequestLike request = ResourcePackRequest.resourcePackRequest()
                            // Reverse the list to apply the packs in the correct order
                            // Velocity applies packs in reverse order
                            .packs(Lists.reverse(applyPacks))
                            .prompt(config.getPrompt())
                            .required(config.getForce())
                            .replace(false);

                    // Clear the current packs and send the new ones
                    player.clearResourcePacks();
                    player.sendResourcePacks(request);
                })
                .delay(1, TimeUnit.SECONDS)
                .schedule();
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
    private @Nullable ResourcePackInfoLike getResourcePackInfo(UUID playerUniqueId, InetAddress address, PackConfig packConfig) {
        PackData result = nekoRpManager.getPackData(playerUniqueId, address, packConfig);
        if (result == null) {
            logger.error("Failed to get pack data, please check your configuration. Pack: {}", packConfig.getConfigPackName());
            return null;
        }
        UUID resourcePackId = UUID.nameUUIDFromBytes(packConfig.getPackPathName().getBytes());
        try {
            HashCode hash = result.getHash();
            ResourcePackInfo.Builder builder = ResourcePackInfo.resourcePackInfo()
                    .id(resourcePackId)
                    .uri(result.getDownloadUrl().toURI());
            if (hash != null) {
                // If the hash is already known, we can set it directly
                return builder.hash(hash.toString()).build();
            }
            // If the hash is not known, we need to compute it and set it in the config
            ResourcePackInfo info;
            try {
                info = builder.computeHashAndBuild().join();
            } catch (CompletionException e) {
                // If the error is not an IOException, log it
                // IOExceptions are expected when the player's limit is reached
                if (!(Throwables.getRootCause(e) instanceof IOException)) {
                    logger.error("Failed to compute hash", e);
                }
                return null;
            }
            config.setPackHash(packConfig.getConfigPackName(), HashCode.fromString(info.hash()));
            return info;
        } catch (URISyntaxException e) {
            logger.error("Failed to parse URI", e);
        }
        return null;
    }

    public <T> void listen(Class<T> eventType, PostOrder order, EventHandler<T> action) {
        server.getEventManager().register(this, eventType, order, action);
    }

    public void reload() {
        server.getEventManager().fire(new NekoRpReloadEvent());
    }
}
