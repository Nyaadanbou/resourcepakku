package cc.mewcraft.nekorp.config

data class PackConfig(
    val configPackName: String,
    /* OSS Path Settings */
    val bucketName: String,
    val packPrefix: String,
    val packPathName: String,
)