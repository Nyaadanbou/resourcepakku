package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.util.reloadable
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path

private const val CONFIG_FILE_NAME = "config.yml"

class NekoRpConfig(
    private val dataDirectory: Path,
) {

    init {
        if (!dataDirectory.toFile().exists()) {
            dataDirectory.toFile().mkdir()

            val loader = YamlConfigurationLoader.builder()
                .path(dataDirectory.resolve(CONFIG_FILE_NAME))
                .nodeStyle(NodeStyle.BLOCK)
                .build()
            val root = loader.createNode()

            root.node("oss", "endpoint").set("oss-cn-hangzhou.aliyuncs.com")
            root.node("oss", "access_key_id").set("accessKeyId")
            root.node("oss", "access_key_secret").set("accessKeySecret")
            root.node("oss", "bucket_name").set("nekorp")
            root.node("oss", "pack_prefix").set("assets/packs/")
            root.node("oss", "pack_name").set("pack.zip")
            root.node("oss", "pack_hash").set("checksum.txt")
            loader.save(root)
        }
    }

    private val loader by reloadable {
        YamlConfigurationLoader.builder()
            .path(dataDirectory.resolve(CONFIG_FILE_NAME))
            .nodeStyle(NodeStyle.BLOCK)
            .build()
    }

    private val root by reloadable { loader.load() }

    val endpoint: String by reloadable { root.node("oss", "endpoint").requireKt() }
    val accessKeyId: String by reloadable { root.node("oss", "access_key_id").requireKt() }
    val accessKeySecret: String by reloadable { root.node("oss", "access_key_secret").requireKt() }

    val bucketName: String by reloadable { root.node("oss", "bucket_name").requireKt() }
    val packPrefix: String by reloadable { root.node("oss", "pack_prefix").requireKt() }
    val packName: String by reloadable { root.node("oss", "pack_name").requireKt() }
    val packHash: String by reloadable { root.node("oss", "pack_hash").requireKt() }
}

private inline fun <reified T> ConfigurationNode.requireKt(): T {
    return this.get(T::class.java) ?: throw IllegalStateException("Missing required value")
}