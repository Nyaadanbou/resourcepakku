package cc.mewcraft.resourcepakku.object2

import cc.mewcraft.resourcepakku.logger
import cc.mewcraft.resourcepakku.plugin
import cc.mewcraft.resourcepakku.serializer.*
import cc.mewcraft.resourcepakku.util.CopyMode
import cc.mewcraft.resourcepakku.util.copyResource
import cc.mewcraft.resourcepakku.util.register
import cc.mewcraft.resourcepakku.util.require
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path
import java.util.*
import kotlin.io.path.readText

/**
 * 封装了插件配置文件 `config.yml` 中的内容.
 *
 * 仅用于方便序列化.
 */
class PluginConfig(
    /**
     * 使用阿里云OSS实现的资源包分发服务.
     */
    val aliyunOssDistService: AliyunOssDistService,
    /**
     * 使用内置HTTP服务器实现的资源包分发服务.
     */
    val selfHostingDistService: SelfHostingDistService,
    /**
     * - key: pack id
     * - val: pack info
     */
    val packInfoById: Map<UUID, PackInfo>,
    /**
     * - key: pack name
     * - val: pack info
     */
    val packInfoByName: Map<String, PackInfo>,
    /**
     * 默认的资源包请求信息.
     */
    val defaultPackRequest: PackRequest,
    /**
     * - key: server name
     * - val: pack request
     *
     * 各个服务器对应的资源包请求.
     */
    val serverPackRequestMap: Map<String, PackRequest>,
) {
    companion object {
        /**
         * 主配置文件的名字.
         */
        private const val MAIN_CONFIG_NAME: String = "config.yml"

        /**
         * 配置文件 `config.yml` 的路径.
         */
        private val mainConfigPath: Path = plugin.dataDirectory.resolve(MAIN_CONFIG_NAME)

        /**
         * 从指定路径加载配置文件, 并反序列化为 [PluginConfig] 对象.
         */
        fun load(): PluginConfig {
            // 初始化配置文件
            copyDefaultConfig()

            // 载入根节点
            val root = YamlConfigurationLoader.builder()
                .indent(2)
                .nodeStyle(NodeStyle.BLOCK)
                .defaultOptions { opts ->
                    opts
                        .shouldCopyDefaults(false)
                        .implicitInitialization(true)
                        .serializers { builder ->
                            builder
                                .register(MainConfigSerializer)
                                .register(AliyunOssDistSerializer)
                                .register(SelfHostingDistSerializer)
                                .register(PackInfoSerializer)
                                .register(PackRequestSerializer)
                                .register(MiniMessageSerializer)
                        }
                }
                .buildAndLoadString(mainConfigPath.readText())

            // 反序列化
            val result = root.require<PluginConfig>()

            return result
        }

        fun copyDefaultConfig() {
            val wrote = copyResource("config.yml", mainConfigPath, mode = CopyMode.SKIP)
            if (wrote) {
                logger.warn("Copied default config.yml to data folder")
            }
        }
    }
}