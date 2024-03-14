package cc.mewcraft.nekorp;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "nekorp", name = "NekoRp", version = "1.0.0", dependencies = {@Dependency(id = "kotlin")}, authors = {"g2213swo"})
public class NekoRp {
    private final ProxyServer server;
    private final NekoRpManager nekoRpManager;

    @Inject
    public NekoRp(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        NekoRpConfig config = new NekoRpConfig(dataDirectory);
        this.nekoRpManager = new NekoRpManager(config,
                new OSSRequester(config),
                logger);
        this.server = server;
    }

    @Subscribe
    private void onLogin(PlayerChooseInitialServerEvent event) {
        PackData result = nekoRpManager.getPackDownloadAddress(
                event.getPlayer().getUniqueId(),
                event.getPlayer().getRemoteAddress().getAddress().getHostAddress()
        );
        String url = result.getDownloadLink();

        var resourcePackInfo = server.createResourcePackBuilder(url)
                .setHash(result.getHash())
                .build();

        event.getPlayer().sendResourcePackOffer(resourcePackInfo);
    }

    @Subscribe
    private void onDisable(ProxyShutdownEvent event) {
        nekoRpManager.onDisable();
    }
}
