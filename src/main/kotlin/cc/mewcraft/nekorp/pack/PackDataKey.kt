package cc.mewcraft.nekorp.pack

import cc.mewcraft.nekorp.config.PackConfig
import java.net.InetAddress
import java.util.*

data class PackDataKey(
    val uuid: UUID,
    val inetAddress: InetAddress,
    val packConfig: PackConfig,
)