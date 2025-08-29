package cc.mewcraft.resourcepakku

import cc.mewcraft.resourcepakku.config.PluginConfig
import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder

class OSSRequester(
    config: PluginConfig,
) {
    private val client: OSS = OSSClientBuilder().build(config.endpoint, config.accessKeyId, config.accessKeySecret)

    fun <T> useClient(block: OSS.() -> T): T {
        return block(client)
    }

    fun close() {
        client.shutdown()
    }
}