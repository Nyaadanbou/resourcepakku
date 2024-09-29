package cc.mewcraft.nekorp.config

import java.nio.file.Path
import java.util.UUID

interface PackConfig {
    val configPackName: String
    val packPath: Path

    val nameUniqueId: UUID
        get() = UUID.nameUUIDFromBytes(configPackName.toByteArray())
}

data class OSSPackConfig(
    override val configPackName: String,
    override val packPath: Path,
    /* OSS Path Settings */
    val bucketName: String,
) : PackConfig