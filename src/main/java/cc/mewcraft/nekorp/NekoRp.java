package cc.mewcraft.nekorp;

import cc.mewcraft.nekorp.command.MainCommand;
import cc.mewcraft.nekorp.event.NekoRpReloadEvent;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.inject.Inject;
import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackInfoLike;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackRequestLike;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Plugin(id = "nekorp", name = "NekoRp", version = "1.0.0", dependencies = {@Dependency(id = "kotlin")}, authors = {"g2213swo"})
public class NekoRp {
    private static NekoRp INSTANCE;

    public static NekoRp getInstance() {
        return INSTANCE;
    }

    public final ProxyServer server;
    public final Logger logger;
    public final Path dataDirectory;

    public NekoRpManager nekoRpManager;
    public NekoRpConfig config;
    public OSSRequester ossRequester;

    @Inject
    public NekoRp(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        INSTANCE = this;
        this.logger = logger;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    private void onProxyInitialization(ProxyInitializeEvent event) {
        this.config = new NekoRpConfig(dataDirectory);
        config.onReload();
        this.ossRequester = new OSSRequester(config);
        this.nekoRpManager = new NekoRpManager(config);

        var mainCommand = new MainCommand();
        server.getCommandManager().register("nekorp", mainCommand);
    }

    @Subscribe
    private @Nullable EventTask onLogin(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String currentServer = event.getServer().getServerInfo().getName();

        //<editor-fold desc="Packs">
        List<PackConfig> currentServerConfigs = config.getServerPacks(currentServer);
        if (currentServerConfigs.isEmpty())
            return null;
        //</editor-fold>

        return EventTask.async(() -> {
            // Get the packs that need to be applied
            List<ResourcePackInfoLike> applyPacks = currentServerConfigs.stream()
                    .map(packConfig -> {
                        UUID resourcePackId = UUID.nameUUIDFromBytes(packConfig.getPackPathName().getBytes());
                        return getResourcePackInfo(resourcePackId, player.getUniqueId(), player.getRemoteAddress().getAddress(), packConfig);
                    })
                    .filter(Objects::nonNull)
                    .toList();

            // Create the request
            ResourcePackRequestLike request = ResourcePackRequest.resourcePackRequest()
                    // Reverse the list to apply the packs in the correct order
                    // Minecraft applies the packs in reverse order
                    .packs(Lists.reverse(applyPacks))
                    .prompt(config.getPrompt())
                    .required(config.getForce())
                    .replace(false);

            // Clear the current packs and send the new ones
            player.clearResourcePacks();
            player.sendResourcePacks(request);
        });
    }

    private @Nullable ResourcePackInfoLike getResourcePackInfo(UUID resourcePackUniqueId, UUID playerUniqueId, InetAddress address, PackConfig packConfig) {
        PackData result = nekoRpManager.getPackData(playerUniqueId, address, packConfig);
        if (result == null) {
            logger.error("Failed to get pack data, please check your configuration. Pack: {}", packConfig.getConfigPackName());
            return null;
        }
        try {
            HashCode hash = result.getHash();
            ResourcePackInfo info;
            ResourcePackInfo.Builder builder = ResourcePackInfo.resourcePackInfo()
                    .id(resourcePackUniqueId)
                    .uri(result.getDownloadUrl().toURI());
            if (hash != null) {
                info = builder.hash(hash.toString()).build();
            } else {
                info = builder.computeHashAndBuild().join();
                config.setPackHash(packConfig.getConfigPackName(), HashCode.fromString(info.hash()));
            }
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
