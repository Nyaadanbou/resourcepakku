package cc.mewcraft.nekorp

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger

@Plugin(
    id = "nekorp",
    name = "NekoRp",
    version = "1.0.0",
    dependencies = [
        Dependency(id = "kotlin", optional = false)
    ],
    authors = ["g2213swo"]
)
class NekoRp @Inject constructor(
    private val nekoRpManager: NekoRpManager,
    private val server: ProxyServer,
    private val logger: Logger,
) {

    @Subscribe
    fun onLogin(e: PlayerChooseInitialServerEvent) {
        val resourcePackInfo = server.createResourcePackBuilder(
            nekoRpManager.getPackDownloadAddress(
                e.player.uniqueId,
                e.player.remoteAddress.address.hostAddress
            ).toString()
        )
            .build()

        e.player.sendResourcePackOffer(resourcePackInfo)
    }

    @Subscribe
    fun onDisable(e: ProxyShutdownEvent) {
        nekoRpManager.onDisable()
    }
}