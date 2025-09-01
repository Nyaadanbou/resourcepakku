package cc.mewcraft.resourcepakku.serializer

import cc.mewcraft.resourcepakku.model.PackInfo
import cc.mewcraft.resourcepakku.util.require
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type
import java.nio.file.Path
import java.util.*

object PackInfoSerializer : TypeSerializer<PackInfo> {

    override fun deserialize(type: Type, node: ConfigurationNode): PackInfo {
        val dataType = node.node("type").require<PackInfo.Type>()
        val id = node.node("id").require<UUID>()
        val name = node.key().toString()
        val path = node.node("path").require<Path>()
        val packInfo = when (dataType) {
            PackInfo.Type.ALIYUN_OSS -> {
                val bucketName = node.node("bucket_name").require<String>()
                val distService = node.hint(REP_HINT_KEY_ALIYUN_OSS_DIST) ?: error("Missing representation hint: ${REP_HINT_KEY_ALIYUN_OSS_DIST.identifier()}")
                PackInfo.aliyunOss(
                    id = id,
                    name = name,
                    path = path,
                    bucketName = bucketName,
                    aliyunDist = distService
                )
            }

            PackInfo.Type.SELF_HOSTING -> {
                val distService = node.hint(REP_HINT_KEY_SELF_HOSTING_DIST) ?: error("Missing representation hint: ${REP_HINT_KEY_SELF_HOSTING_DIST.identifier()}")
                PackInfo.selfHosting(
                    id = id,
                    name = name,
                    path = path,
                    selfHostingDist = distService
                )
            }

            else -> {
                error("Can't load pack info type: $dataType")
            }
        }

        return packInfo
    }

    override fun serialize(type: Type, obj: PackInfo?, node: ConfigurationNode) {
        throw UnsupportedOperationException()
    }
}