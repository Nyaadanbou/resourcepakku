package cc.mewcraft.nekorp.config

import cc.mewcraft.nekorp.event.NekoRpReloadEvent
import cc.mewcraft.nekorp.util.listen
import cc.mewcraft.nekorp.util.plugin
import cc.mewcraft.nekorp.util.reloadable
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.outputStream

private const val CONFIG_FILE_NAME = "config.yml"

class NekoRpConfig(
    dataDirectory: Path,
) {
    companion object ConfigUtil {
        fun toNameUUID(packConfig: PackConfig): UUID = UUID.nameUUIDFromBytes(packConfig.packPathName.toByteArray())
    }

    private val path = dataDirectory.resolve(CONFIG_FILE_NAME)
    private val loader: YamlConfigurationLoader by reloadable {
        YamlConfigurationLoader.builder()
            .path(path)
            .nodeStyle(NodeStyle.BLOCK)
            .build()
    }

    // Key: Server Name
    // Val: Pack Name
    private lateinit var serverPackMap: ImmutableMultimap<String, PackConfig>

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
        val serverPackMap: Multimap<String, PackConfig> = MultimapBuilder
            .hashKeys()
            .linkedHashSetValues()
            .build()
        serverNode.childrenMap().forEach { (serverName, serverNode) ->
            serverPackMap.putAll(serverName.toString(), createServerPackConfig(serverNode))
        }
        this.serverPackMap = ImmutableMultimap.copyOf(serverPackMap)
    }

    val endpoint: String by reloadable { ossNode.node("endpoint").requireKt<String>() }
    val accessKeyId: String by reloadable { ossNode.node("access_key_id").requireKt<String>() }
    val accessKeySecret: String by reloadable { ossNode.node("access_key_secret").requireKt<String>() }
    val expireSeconds: Long by reloadable { ossNode.node("expire_seconds").requireKt<Long>() }

    /* ResourcePack Settings */
    val limitSeconds: Long by reloadable { resourcePackNode.node("limit_seconds").requireKt<Long>() }
    val prompt: Component by reloadable { MiniMessage.miniMessage().deserialize(resourcePackNode.node("prompt").requireKt()) }
    val force: Boolean by reloadable { resourcePackNode.node("force").requireKt<Boolean>() }

    private val defaultServerSettings: List<PackConfig> by reloadable { createServerPackConfig(defaultServerNode) }

    fun getServerPacks(serverName: String): List<PackConfig> {
        if (!serverPackMap.containsKey(serverName))
            return defaultServerSettings
        return serverPackMap[serverName].toList()
    }

    fun getPackConfigFromNameUUID(uniqueId: UUID): PackConfig? {
        return serverPackMap.values().find { UUID.nameUUIDFromBytes(it.packPathName.toByteArray()) == uniqueId }
    }

    private fun initConfig() {
        plugin.javaClass.getResourceAsStream("/$CONFIG_FILE_NAME")?.buffered()?.use {
            it.copyTo(path.outputStream().buffered())
        }
    }

    private fun createServerPackConfig(serverNode: ConfigurationNode): List<PackConfig> {
        val packNames = serverNode.requireKt<List<String>>()
        return packNames.map { packNode.node(it) }
            .map { createPackConfig(it.key().toString(), it) }
    }

    private fun createPackConfig(nodeName: String, node: ConfigurationNode): PackConfig {
        return PackConfig(
            configPackName = nodeName,
            bucketName = node.node("oss_path", "bucket_name").requireKt(),
            packPrefix = node.node("oss_path", "pack_prefix").requireKt(),
            packPathName = node.node("oss_path", "pack_name").requireKt(),
        )
    }
}

private inline fun <reified T> ConfigurationNode.requireKt(): T {
    return this.get<T>() ?: throw IllegalStateException("Missing required value at '${this.path().joinToString(".")}'")
}