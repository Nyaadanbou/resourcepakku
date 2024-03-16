package cc.mewcraft.nekorp;

import cc.mewcraft.nekorp.command.MainCommand;
import cc.mewcraft.nekorp.event.NekoRpReloadEvent;
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
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import org.slf4j.Logger;

import java.nio.file.Path;

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
    private EventTask onLogin(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String currentServer = event.getServer().getServerInfo().getName();
        ServerConfig serverConfig = config.getServerConfig(currentServer);

        // 延迟发送，否则会被覆盖
        return EventTask.async(() -> {
            PackData result = nekoRpManager.getPackData(
                    player.getUniqueId(),
                    player.getRemoteAddress().getAddress(),
                    serverConfig
            );
            String url = result.getDownloadUrl().toString();

            ResourcePackInfo resourcePackInfo = server.createResourcePackBuilder(url)
                    .setHash(result.getHash().asBytes())
                    .setPrompt(serverConfig.getPrompt())
                    .setShouldForce(serverConfig.getForce())
                    .build();
            player.sendResourcePackOffer(resourcePackInfo);
        });
    }

    @Subscribe
    private void onReload(NekoRpReloadEvent event) {
        config.onReload();
    }

    public <T> void listen(Class<T> eventType, PostOrder order, EventHandler<T> action) {
        server.getEventManager().register(this, eventType, order, action);
    }

    public void reload() {
        server.getEventManager().fire(new NekoRpReloadEvent());
    }
}
