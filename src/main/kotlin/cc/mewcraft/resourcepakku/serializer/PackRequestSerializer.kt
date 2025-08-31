package cc.mewcraft.resourcepakku.serializer

import cc.mewcraft.resourcepakku.logger
import cc.mewcraft.resourcepakku.object2.PackRequest
import cc.mewcraft.resourcepakku.util.require
import net.kyori.adventure.text.Component
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

object PackRequestSerializer : TypeSerializer<PackRequest> {

    override fun deserialize(type: Type, node: ConfigurationNode): PackRequest {
        val packInfos = node.hint(REP_HINT_KEY_PACK_INFO_BY_NAME) ?: error("Missing representation hint: ${REP_HINT_KEY_PACK_INFO_BY_NAME.identifier()}")
        val packNames = node.node("packs").get<List<String>>(emptyList())
        val packs = packNames.mapNotNull { name ->
            packInfos[name] ?: run {
                logger.error("PackInfo not found for {} while loading PackRequest. The PackInfo will be effectively ignored.", name)
                null
            }
        }
        val force = node.node("force").require<Boolean>()
        val prompt = node.node("prompt").require<Component>()

        return PackRequest.packRequest(
            packs = packs,
            force = force,
            prompt = prompt,
        )
    }

    override fun serialize(type: Type, obj: PackRequest?, node: ConfigurationNode) {
        throw UnsupportedOperationException()
    }
}