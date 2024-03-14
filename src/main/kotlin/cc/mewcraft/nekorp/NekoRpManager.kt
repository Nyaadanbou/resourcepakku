@file:Suppress("UnstableApiUsage")

package cc.mewcraft.nekorp

import com.aliyun.oss.HttpMethod
import com.aliyun.oss.model.GeneratePresignedUrlRequest
import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import com.google.common.collect.Tables
import com.google.common.hash.HashCode
import org.slf4j.Logger
import java.util.*

data class PackData(
    val downloadLink: String,
    val hash: ByteArray?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PackData

        if (downloadLink != other.downloadLink) return false
        if (hash != null) {
            if (other.hash == null) return false
            if (!hash.contentEquals(other.hash)) return false
        } else if (other.hash != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = downloadLink.hashCode()
        result = 31 * result + (hash?.contentHashCode() ?: 0)
        return result
    }
}

class NekoRpManager(
    private val config: NekoRpConfig,
    private val requester: OSSRequester,
    private val logger: Logger,
) {
    // Key: UUID, Row: Player IP, Value: Download Link
    private val downloadLinkTable: Table<UUID, String, PackData> = Tables.synchronizedTable(HashBasedTable.create())
    private var oldPackData: PackData? = null
        set(value) {
            if (value == null)
                return
            if (field == null) {
                field = value
                return
            }

            if (field!!.hash.contentEquals(value.hash)) {
                downloadLinkTable.clear()
            }
        }

    private val bucketName: String
        get() = config.bucketName
    private val packPrefix: String
        get() = config.packPrefix
    private val packName: String
        get() = config.packName
    private val packHash: String
        get() = config.packHash

    fun getPackDownloadAddress(uniqueId: UUID, playerIp: String): PackData {
        if (downloadLinkTable.contains(uniqueId, playerIp)) {
            return downloadLinkTable.get(uniqueId, playerIp)!!
        }

        val generatedLink = requester.useClient {
            val objectName = "${packPrefix}${packName}"
            val objectMetadata = getObjectMetadata(bucketName, objectName)
            if (objectMetadata.contentType != "application/zip") {
                throw IllegalStateException("Pack file is not a zip file")
            }
            val request = GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.GET)
                .apply {
                    // Set the expiration time of the URL to 30 minutes from now
                    expiration = Date(System.currentTimeMillis() + 1 * 60 * 1000)
                }
            generatePresignedUrl(request)
        }

        val hash = requester.useClient {
            val objectName = "${packPrefix}${packHash}"
            val fileObject = getObject(bucketName, objectName)
            if (fileObject.objectMetadata.contentType != "text/plain") {
                throw IllegalStateException("Checksum file is not a text file")
            }

            val objectContent = fileObject.objectContent
            val text = objectContent.bufferedReader().use { it.readText() }
            HashCode.fromString(text)
        }

        val result = PackData(generatedLink.toString(), hash.asBytes())
        downloadLinkTable.put(uniqueId, playerIp, result)
        oldPackData = result

        return result
            .also { logger.info("Generated download link for $uniqueId ($playerIp): $it") }
    }

    fun onDisable() {
        downloadLinkTable.clear()
        requester.close()
    }
}