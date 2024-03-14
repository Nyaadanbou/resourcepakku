package cc.mewcraft.nekorp

import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder

class OSSRequester(
    config: NekoRpConfig
) {
    private val client: OSS = OSSClientBuilder().build(config.endpoint, config.accessKeyId, config.accessKeySecret)

    fun <T> useClient(block: OSS.() -> T): T {
        return block(client)
    }

    fun close() {
        client.shutdown()
    }
}