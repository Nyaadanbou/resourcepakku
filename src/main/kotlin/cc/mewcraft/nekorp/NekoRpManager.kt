package cc.mewcraft.nekorp

import com.aliyun.oss.HttpMethod
import com.aliyun.oss.model.GeneratePresignedUrlRequest
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.google.common.collect.Tables
import com.google.inject.Inject
import org.slf4j.Logger
import java.net.URI
import java.net.URL
import java.util.*

class NekoRpManager @Inject constructor(
    private val requester: OSSRequester,
    private val logger: Logger,
) {
    // Key: UUID, Row: Player IP, Value: Download Link
    private val downloadLinkTable: Table<UUID, String, String> = Tables.synchronizedTable(HashBasedTable.create())

    fun getPackDownloadAddress(uniqueId: UUID, playerIp: String): URL {
        if (downloadLinkTable.contains(uniqueId, playerIp)) {
            return URI(downloadLinkTable.get(uniqueId, playerIp)).toURL()
        }

        val generated = requester.useClient {
            val bucketName = "g2213swo"
            val objectName = "assets/pack.zip"
            val metadata = getObjectMetadata(bucketName, objectName)
            val eTag = metadata.eTag
            val request = GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.GET)
                .apply {
                    // Set the expiration time of the URL to 30 minutes from now
                    expiration = Date(System.currentTimeMillis() + 30 * 60 * 1000)
                }
            generatePresignedUrl(request)
        }

        downloadLinkTable.put(uniqueId, playerIp, generated.toString())
        return generated.also { logger.info("Generated download link for $uniqueId ($playerIp): $it") }
    }

    fun onDisable() {
        downloadLinkTable.clear()
        requester.close()
    }
}