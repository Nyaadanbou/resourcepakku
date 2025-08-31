package cc.mewcraft.resourcepakku.serializer

import cc.mewcraft.resourcepakku.object2.*
import cc.mewcraft.resourcepakku.util.representationHint
import cc.mewcraft.resourcepakku.util.require
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type
import java.util.*

internal val REP_HINT_KEY_ALIYUN_OSS_DIST = representationHint<AliyunOssDistService>("aliyun_oss_dist")
internal val REP_HINT_KEY_SELF_HOSTING_DIST = representationHint<SelfHostingDistService>("self_hosting_dist")
internal val REP_HINT_KEY_PACK_INFO_BY_NAME = representationHint<Map<String, PackInfo>>("pack_info_by_name")
internal val REP_HINT_KEY_PACK_INFO_BY_ID = representationHint<Map<UUID, PackInfo>>("pack_info_by_id")

object MainConfigSerializer : TypeSerializer<PluginConfig> {

    override fun deserialize(type: Type, node: ConfigurationNode): PluginConfig {
        val aliyunOssDist = node.node("services", "aliyun_oss").require<AliyunOssDistService>()
        node.hint(REP_HINT_KEY_ALIYUN_OSS_DIST, aliyunOssDist)

        val selfHostingDist = node.node("services", "self_hosting").require<SelfHostingDistService>()
        node.hint(REP_HINT_KEY_SELF_HOSTING_DIST, selfHostingDist)

        val packInfoByName = node.node("pack_infos").require<Map<String, PackInfo>>()
        node.hint(REP_HINT_KEY_PACK_INFO_BY_NAME, packInfoByName)

        val packInfoById = packInfoByName.values.associateBy { it.id }
        node.hint(REP_HINT_KEY_PACK_INFO_BY_ID, packInfoById)

        val defaultPackRequest = node.node("default_pack_request").require<PackRequest>()
        val serverPackRequestMap = node.node("server_pack_requests").require<Map<String, PackRequest>>()

        return PluginConfig(
            aliyunOssDistService = aliyunOssDist,
            selfHostingDistService = selfHostingDist,
            packInfoByName = packInfoByName,
            packInfoById = packInfoById,
            defaultPackRequest = defaultPackRequest,
            serverPackRequestMap = serverPackRequestMap,
        )
    }

    override fun serialize(type: Type, obj: PluginConfig?, node: ConfigurationNode) {
        throw UnsupportedOperationException()
    }
}