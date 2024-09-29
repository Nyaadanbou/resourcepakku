package cc.mewcraft.nekorp.config

import cc.mewcraft.nekorp.event.NekoRpReloadEvent
import cc.mewcraft.nekorp.plugin
import cc.mewcraft.nekorp.util.listen
import cc.mewcraft.nekorp.util.reloadable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.outputStream

private const val CONFIG_FILE_NAME = "config.yml"

class NekoRpConfig(
    dataDirectory: Path,
) {
    private val path = dataDirectory.resolve(CONFIG_FILE_NAME)
    private val loader: YamlConfigurationLoader by reloadable {
        YamlConfigurationLoader.builder()
            .path(path)
            .nodeStyle(NodeStyle.BLOCK)
            .build()
    }

    // Key: Server name
    // Value: Pack configs
    // 如果某个服务器没有配置, 则使用默认服务器的配置
    // 如果某个服务器配置为空列表, 则不会加载任何资源包
    private lateinit var serverPackMap: Map<String, PackConfigs>

    private val root: ConfigurationNode by reloadable { loader.load() }

    private val ossNode: ConfigurationNode
        get() = root.node("oss")
    private val resourcePackNode: ConfigurationNode
        get() = root.node("resource_pack")
    private val serverNode: ConfigurationNode
        get() = root.node("servers")
    private val packNode: ConfigurationNode
        get() = root.node("packs")
    private val defaultServerNode: ConfigurationNode
        get() = root.node("server_default")

    init {
        plugin.listen<NekoRpReloadEvent> { onReload() }
    }

    fun onReload() {
        if (!path.exists()) {
            initConfig()
        }
        val serverPackMap = hashMapOf<String, PackConfigs>()
        serverNode.childrenMap().forEach { (serverName, serverNode) ->
            serverPackMap[serverName.toString()] = createServerPackConfigs(serverNode)
        }
        this.serverPackMap = serverPackMap
    }

    val endpoint: String by reloadable { ossNode.node("endpoint").requireKt<String>() }
    val accessKeyId: String by reloadable { ossNode.node("access_key_id").requireKt<String>() }
    val accessKeySecret: String by reloadable { ossNode.node("access_key_secret").requireKt<String>() }
    val expireSeconds: Long by reloadable { ossNode.node("expire_seconds").requireKt<Long>() }

    /* ResourcePack Settings */
    val limitSeconds: Long by reloadable { resourcePackNode.node("limit_seconds").requireKt<Long>() }
    val prompt: Component by reloadable { MiniMessage.miniMessage().deserialize(resourcePackNode.node("prompt").requireKt()) }
    val force: Boolean by reloadable { resourcePackNode.node("force").requireKt<Boolean>() }

    private val defaultServerSettings: PackConfigs by reloadable { createServerPackConfigs(defaultServerNode) }

    fun getServerPackConfigs(serverName: String): PackConfigs {
        if (serverPackMap.containsKey(serverName))
            return serverPackMap[serverName]!!
        return defaultServerSettings
    }

    fun getPackConfigFromNameUUID(uniqueId: UUID): PackConfig? {
        return defaultServerSettings.find { it.nameUniqueId == uniqueId }
            ?: serverPackMap.values.flatten().find { it.nameUniqueId == uniqueId }
    }

    private fun initConfig() {
        plugin.javaClass.getResourceAsStream("/$CONFIG_FILE_NAME")?.buffered()?.use {
            it.copyTo(path.outputStream().buffered())
        }
    }

    private fun createServerPackConfigs(serverNode: ConfigurationNode): PackConfigs {
        val packNames = serverNode.requireKt<List<String>>()
        if (packNames.isEmpty())
            return EmptyPackConfigs
        return packNames
            .map { packNode.node(it) }
            .map { createPackConfig(it.key().toString(), it) }
            .let { PackConfigs.of(it) }
    }

    private fun createPackConfig(nodeName: String, node: ConfigurationNode): PackConfig {
        return OSSPackConfig(
            configPackName = nodeName,
            packPath = node.node("oss_path", "pack_path").requireKt<String>().let { Path(it) },
            bucketName = node.node("oss_path", "bucket_name").requireKt()
        )
    }
}

private inline fun <reified T> ConfigurationNode.requireKt(): T {
    return this.get<T>() ?: throw IllegalStateException("Missing required value at '${this.path().joinToString(".")}'")
}