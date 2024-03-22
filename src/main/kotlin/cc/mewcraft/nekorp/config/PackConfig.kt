package cc.mewcraft.nekorp.config

import com.google.common.hash.HashCode

data class PackConfig(
    val configPackName: String,
    /* OSS Path Settings */
    val bucketName: String,
    val packPrefix: String,
    val packPathName: String,
    val packHash: String?,
) {
    fun packHashCode(): HashCode? = runCatching { packHash?.let { HashCode.fromString(it) } }.getOrNull()
}