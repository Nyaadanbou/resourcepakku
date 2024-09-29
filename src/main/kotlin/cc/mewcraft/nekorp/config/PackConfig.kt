package cc.mewcraft.nekorp.config

import java.nio.file.Path

interface PackConfig {
    val configPackName: String
    val packPath: Path
}

data class OSSPackConfig(
    override val configPackName: String,
    override val packPath: Path,
    /* OSS Path Settings */
    val bucketName: String,
) : PackConfig {

}