package cc.mewcraft.resourcepakku

import cc.mewcraft.resourcepakku.command.MainCommand
import cc.mewcraft.resourcepakku.config.PluginConfig
import cc.mewcraft.resourcepakku.event.ResourcePakkuReloadEvent
import cc.mewcraft.resourcepakku.pack.ResourcePakkuManager
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
    id = "resourcepakku",
    name = "resourcepakku",
    version = "1.0.0",
    dependencies = [Dependency(id = "kotlin")],
    authors = ["g2213swo"]
)
class ResourcePakkuPlugin @Inject constructor(
    val server: ProxyServer,
    val logger: Logger,
    @DataDirectory
    private val dataDirectory: Path,
) {

    companion object {
        internal var INSTANCE: ResourcePakkuPlugin? = null
    }

    lateinit var config: PluginConfig
    lateinit var ossRequester: OSSRequester
    lateinit var resourcePakkuManager: ResourcePakkuManager
    lateinit var resourcePackListener: ResourcePackListener

    @Subscribe
    private fun onProxyInitialization(event: ProxyInitializeEvent) {
        INSTANCE = this
        this.config = PluginConfig(dataDirectory)
        config.onReload()
        this.ossRequester = OSSRequester(config)
        this.resourcePakkuManager = ResourcePakkuManager(logger, config)
        this.resourcePackListener = ResourcePackListener(this)
        server.eventManager.register(this, resourcePackListener)

        val mainCommand = MainCommand()
        val commandManager = server.commandManager
        commandManager.register(
            commandManager.metaBuilder("resourcepakku")
                .plugin(this)
                .build(),
            mainCommand
        )
    }

    @Subscribe
    private fun onProxyShutdown(event: ProxyShutdownEvent) {
        INSTANCE = null
        resourcePakkuManager.onDisable()
        server.eventManager.unregisterListener(this, resourcePackListener)
    }

    fun <T> listen(eventType: Class<T>?, order: PostOrder?, action: EventHandler<T>?) {
        server.eventManager.register(this, eventType, order, action)
    }

    fun reload() {
        server.eventManager.fire(ResourcePakkuReloadEvent())
    }
}

internal val plugin: ResourcePakkuPlugin
    get() = ResourcePakkuPlugin.INSTANCE ?: throw IllegalStateException("Plugin not initialized")
