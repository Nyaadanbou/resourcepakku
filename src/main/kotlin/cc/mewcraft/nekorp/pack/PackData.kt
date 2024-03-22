package cc.mewcraft.nekorp.pack

import com.google.common.hash.HashCode
import java.net.URL

data class PackData(
    val downloadUrl: URL,
    /**
     * 此文件的 SHA-1 值
     *
     * @return null 代表未提供哈希
     */
    val hash: HashCode?,
)