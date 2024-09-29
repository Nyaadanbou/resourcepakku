package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.command.MainCommand
import cc.mewcraft.nekorp.config.NekoRpConfig
import cc.mewcraft.nekorp.event.NekoRpReloadEvent
import cc.mewcraft.nekorp.pack.NekoRpManager
import com.google.inject.Inject
import com.velocitypowered.api.event.EventHandler
import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Dependency
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "nekorp",
    name = "NekoRp",
    version = "1.0.0",
    dependencies = [Dependency(id = "kotlin")],
    authors = ["g2213swo"]
)
class NekoRp @Inject constructor(
    val server: ProxyServer,
    val logger: Logger,
    @DataDirectory
    private val dataDirectory: Path,
) {

    companion object {
        internal var INSTANCE: NekoRp? = null
    }

    lateinit var config: NekoRpConfig
    lateinit var ossRequester: OSSRequester
    lateinit var nekoRpManager: NekoRpManager
    lateinit var resourcePackListener: ResourcePackListener

    @Subscribe
    private fun onProxyInitialization(event: ProxyInitializeEvent) {
        INSTANCE = this
        this.config = NekoRpConfig(dataDirectory)
        config.onReload()
        this.ossRequester = OSSRequester(config)
        this.nekoRpManager = NekoRpManager(logger, config)
        this.resourcePackListener = ResourcePackListener(this)
        server.eventManager.register(this, resourcePackListener)

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
        server.eventManager.unregisterListener(this, resourcePackListener)
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