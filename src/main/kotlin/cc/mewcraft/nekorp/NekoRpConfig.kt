package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.util.reloadable
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists

private const val CONFIG_FILE_NAME = "config.yml"

data class ServerConfig(
    /* OSS Settings */
    val packPrefix: String,
    val packName: String,
    val packHash: String,
    /* ResourcePack Settings */
    val prompt: Component,
    val force: Boolean,
)

class NekoRpConfig(
    private val dataDirectory: Path,
) {
    private val path = dataDirectory.resolve(CONFIG_FILE_NAME)
    private val loader: YamlConfigurationLoader = YamlConfigurationLoader.builder()
        .path(path)
        .nodeStyle(NodeStyle.BLOCK)
        .build()

    // Key: Server Name
    // Val: Server Config
    private val serverConfigMap: MutableMap<String, ServerConfig> = ConcurrentHashMap()

    fun getServerConfig(serverName: String): ServerConfig {
        return serverConfigMap[serverName] ?: requireNotNull(serverConfigMap["default"])
    }

    private val root: ConfigurationNode by reloadable { loader.load() }

    fun onReload() {
        serverConfigMap.clear()
        if (!path.exists()) {
            initConfig()
        }

        root.node("servers").childrenMap().forEach { (serverName, serverNode) ->
            serverConfigMap[serverName.toString()] = createServerConfig(serverNode)
        }
    }

    val endpoint: String by reloadable { root.node("oss", "endpoint").requireKt<String>() }
    val accessKeyId: String by reloadable { root.node("oss", "access_key_id").requireKt<String>() }
    val accessKeySecret: String by reloadable { root.node("oss", "access_key_secret").requireKt<String>() }
    val bucketName: String by reloadable { root.node("oss", "bucket_name").requireKt<String>() }
    val expireSeconds: Long by reloadable { root.node("oss", "expire_seconds").requireKt<Long>() }

    private fun initConfig() {
        dataDirectory.toFile().mkdir()
        val root = loader.createNode()
        // OSS Settings
        root.node("oss", "endpoint").set("oss-cn-hangzhou.aliyuncs.com")
        root.node("oss", "access_key_id").set("accessKeyId")
        root.node("oss", "access_key_secret").set("accessKeySecret")
        root.node("oss", "bucket_name").set("nekorp")
        root.node("oss", "expire_seconds").set(1800L)

        val serverNode = root.node("servers")
        val defaultNode = serverNode.node("default")
        defaultNode.node("oss", "pack_prefix").set("assets/packs/")
        defaultNode.node("oss", "pack_name").set("pack.zip")
        defaultNode.node("oss", "pack_hash").set("checksum.txt")

        // ResourcePack Settings
        defaultNode.node("resource_pack", "prompt").set("<yellow>Rewrite the prompt here.")
        defaultNode.node("resource_pack", "force").set(false)

        loader.save(root)
    }

    private fun createServerConfig(node: ConfigurationNode): ServerConfig {
        return ServerConfig(
            packPrefix = node.node("oss", "pack_prefix").requireKt(),
            packName = node.node("oss", "pack_name").requireKt(),
            packHash = node.node("oss", "pack_hash").requireKt(),
            prompt = MiniMessage.miniMessage().deserialize(node.node("resource_pack", "prompt").requireKt()),
            force = node.node("resource_pack", "force").requireKt(),
        )
    }
}

private inline fun <reified T> ConfigurationNode.requireKt(): T {
    return this.get<T>() ?: throw IllegalStateException("Missing required value at '${this.path().joinToString(".")}'")
}