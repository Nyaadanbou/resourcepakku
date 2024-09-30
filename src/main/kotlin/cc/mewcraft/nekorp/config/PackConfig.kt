package cc.mewcraft.nekorp.config

import cc.mewcraft.nekorp.plugin
import com.google.common.base.Throwables
import com.google.common.hash.HashCode
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackInfoLike
import org.jetbrains.annotations.Blocking
import java.io.IOException
import java.net.InetAddress
import java.net.URISyntaxException
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletionException
import kotlin.io.path.toPath

interface PackConfig {
    val configPackName: String
    val packPath: Path
    val uniqueId: UUID

    fun getResourcePackInfo(playerUniqueId: UUID, address: InetAddress): ResourcePackInfoLike?
}

class OSSPackConfig(
    override val configPackName: String,
    override val packPath: Path,
    /* OSS Path Settings */
    val bucketName: String,
) : PackConfig {
    override val uniqueId: UUID = UUID.nameUUIDFromBytes(configPackName.toByteArray())

    override fun getResourcePackInfo(playerUniqueId: UUID, address: InetAddress): ResourcePackInfoLike? {
        return PackConfigSupport.getResourcePackInfo(playerUniqueId, address, this)
    }

    override fun hashCode(): Int {
        return uniqueId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OSSPackConfig) return false
        return uniqueId == other.uniqueId
    }
}

class MinecraftPackConfig(
    private val resourcePackInfo: ResourcePackInfoLike,
) : PackConfig {
    override val configPackName: String = resourcePackInfo.asResourcePackInfo().id().toString()
    override val packPath: Path = runCatching { resourcePackInfo.asResourcePackInfo().uri().toPath() }.getOrDefault(Path.of("."))
    override val uniqueId: UUID = resourcePackInfo.asResourcePackInfo().id()

    override fun getResourcePackInfo(playerUniqueId: UUID, address: InetAddress): ResourcePackInfoLike {
        return resourcePackInfo
    }

    override fun hashCode(): Int {
        return uniqueId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MinecraftPackConfig) return false
        return uniqueId == other.uniqueId
    }
}

data object EmptyPackConfig : PackConfig {
    override val configPackName: String = "EmptyPack"
    override val packPath: Path = Path.of(".")
    override val uniqueId: UUID = UUID(0, 0)

    override fun getResourcePackInfo(playerUniqueId: UUID, address: InetAddress): ResourcePackInfoLike? {
        return null
    }
}

private data object PackConfigSupport {
    private val logger = plugin.logger
    private val nekoRpManager = plugin.nekoRpManager

    /**
     * Get the ResourcePackInfo for a pack.
     *
     * @param playerUniqueId The unique id of the player.
     * @param address        The address of the player.
     * @param packConfig     The pack configuration.
     *
     * @return The ResourcePackInfo or null if an error occurred.
     */
    @Blocking
    fun getResourcePackInfo(playerUniqueId: UUID, address: InetAddress, packConfig: PackConfig): ResourcePackInfoLike? {
        val result = nekoRpManager.getPackData(playerUniqueId, address, packConfig)
        if (result == null) {
            logger.error("Failed to get pack data, please check your configuration. Pack: {}", packConfig.configPackName)
            return null
        }
        try {
            val hash = nekoRpManager.getComputedPackHash(packConfig)
            val nameUniqueId = packConfig.uniqueId
            logger.info("Sending pack {} to player {}, pack name unique id: {}", packConfig.configPackName, playerUniqueId, nameUniqueId)
            val builder = ResourcePackInfo.resourcePackInfo()
                .id(nameUniqueId)
                .uri(result.downloadUrl.toURI())
            if (hash != null) {
                // If the hash is already known, we can set it directly
                return builder.hash(hash.toString()).build()
            }
            // If the hash is not known, we need to compute it and set it in the config
            val info: ResourcePackInfo
            try {
                info = builder.computeHashAndBuild().join()
            } catch (e: CompletionException) {
                // If the error is not an IOException, log it
                // IOExceptions are expected when the player's limit is reached
                if (Throwables.getRootCause(e) !is IOException) {
                    logger.error("Failed to compute hash", e)
                }
                logger.info("Blocked downloading request {} from player {}", packConfig.configPackName, playerUniqueId)
                return null
            }
            nekoRpManager.putComputedPackHash(packConfig, HashCode.fromString(info.hash()))
            return info
        } catch (e: URISyntaxException) {
            logger.error("Failed to parse URI", e)
        }
        return null
    }
}