package cc.mewcraft.resourcepakku.serializer

import cc.mewcraft.resourcepakku.model.SelfHostingDistService
import cc.mewcraft.resourcepakku.plugin
import cc.mewcraft.resourcepakku.util.require
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type
import java.nio.file.Path

object SelfHostingDistSerializer : TypeSerializer<SelfHostingDistService> {

    override fun deserialize(type: Type, node: ConfigurationNode): SelfHostingDistService {
        val enabled = node.node("enabled").require<Boolean>()
        val host = node.node("host").require<String>()
        val port = node.node("port").require<Int>()
        val validOnly = node.node("valid_only").require<Boolean>()
        val rootPath = node.node("web_root").get<Path>(plugin.dataDirectory.resolve("webserver"))

        return SelfHostingDistService(
            enabled = enabled,
            host = host,
            port = port,
            validOnly = validOnly,
            rootPath = rootPath
        )
    }

    override fun serialize(type: Type, obj: SelfHostingDistService?, node: ConfigurationNode) {
        throw UnsupportedOperationException()
    }
}