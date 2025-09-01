package cc.mewcraft.resourcepakku.serializer

import cc.mewcraft.resourcepakku.model.AliyunOssDistService
import cc.mewcraft.resourcepakku.util.require
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

object AliyunOssDistSerializer : TypeSerializer<AliyunOssDistService> {

    override fun deserialize(type: Type, node: ConfigurationNode): AliyunOssDistService {
        val endpoint = node.node("endpoint").require<String>()
        val accessKeyId = node.node("access_key_id").require<String>()
        val accessKeySecret = node.node("access_key_secret").require<String>()
        val presignedUrlExpireSeconds = node.node("presigned_url_expire_seconds").require<Long>()
        val newPresignedUrlIntervalSeconds = node.node("new_presigned_url_interval_seconds").require<Long>()

        return AliyunOssDistService(
            endpoint = endpoint,
            accessKeyId = accessKeyId,
            accessKeySecret = accessKeySecret,
            presignedUrlExpireSeconds = presignedUrlExpireSeconds,
            newPresignedUrlIntervalSeconds = newPresignedUrlIntervalSeconds,
        )
    }

    override fun serialize(type: Type, obj: AliyunOssDistService?, node: ConfigurationNode) {
        throw UnsupportedOperationException()
    }
}