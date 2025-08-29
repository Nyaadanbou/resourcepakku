package cc.mewcraft.resourcepakku.pack

import cc.mewcraft.resourcepakku.config.PackConfig
import java.net.InetAddress
import java.util.*

data class PackDataKey(
    val uuid: UUID,
    val inetAddress: InetAddress,
    val packConfig: PackConfig,
)