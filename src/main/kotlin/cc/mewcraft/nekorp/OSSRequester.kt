package cc.mewcraft.nekorp

import com.aliyun.oss.OSS
import com.aliyun.oss.OSSClientBuilder

class OSSRequester(
    endpoint: String,
    accessKeyId: String,
    accessKeySecret: String,
) {
    private val client: OSS = OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret)

    fun <T> useClient(block: OSS.() -> T): T {
        return block(client)
    }

    fun close() {
        client.shutdown()
    }
}