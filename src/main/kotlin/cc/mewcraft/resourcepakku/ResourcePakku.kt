package cc.mewcraft.resourcepakku

import cc.mewcraft.resourcepakku.command.DispatchCommand
import cc.mewcraft.resourcepakku.manager.ResourcePackController
import cc.mewcraft.resourcepakku.object2.PluginConfig
import cc.mewcraft.resourcepakku.object2.WebFiles
import com.google.inject.Inject
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

private object ResourcePakkuPluginHolder {
    var INSTANCE: ResourcePakku? = null
}

internal val plugin: ResourcePakku
    get() = ResourcePakkuPluginHolder.INSTANCE ?: throw IllegalStateException("Plugin not initialized")

internal val logger: Logger
    get() = plugin.logger

internal val server: ProxyServer
    get() = plugin.server

@Plugin(
    id = "resourcepakku",
    name = "resourcepakku",
    version = "1.0.0",
    dependencies = [Dependency(id = "kotlin")],
    authors = ["Akiranya", "g2213swo"]
)
class ResourcePakku
@Inject constructor(
    val server: ProxyServer,
    val logger: Logger,
    @param:DataDirectory
    val dataDirectory: Path,
) {
    private lateinit var pluginConfig: PluginConfig
    private lateinit var packController: ResourcePackController

    inline fun <reified T : Any> listen(order: PostOrder = PostOrder.NORMAL, noinline action: (T) -> Unit) {
        server.eventManager.register(this, T::class.java, order, action)
    }

    fun reloadPlugin() {
        disablePlugin()
        enablePlugin()
    }

    fun enablePlugin() {
        // 初始化配置文件
        pluginConfig = PluginConfig.load()

        // 初始化网页文件
        WebFiles.copyDefaultWeb()

        // 初始化资源包处理逻辑
        packController = ResourcePackController.fromPluginConfig(pluginConfig)
        packController.start()
    }

    fun disablePlugin() {
        packController.close()
    }

    @Subscribe
    private fun on(event: ProxyInitializeEvent) {
        // 启动时首先赋值插件实例
        ResourcePakkuPluginHolder.INSTANCE = this

        // 执行插件启动逻辑
        enablePlugin()

        // 注册指令 (放在最后)
        val commandManager = server.commandManager
        val commandMeta = commandManager
            .metaBuilder("resrcpakku")
            .plugin(this)
            .build()
        commandManager.register(commandMeta, DispatchCommand())
    }

    @Subscribe
    private fun on(event: ProxyShutdownEvent) {
        // 执行插件关闭逻辑
        disablePlugin()

        // 关闭时最后清空插件实例
        ResourcePakkuPluginHolder.INSTANCE = null
    }
}