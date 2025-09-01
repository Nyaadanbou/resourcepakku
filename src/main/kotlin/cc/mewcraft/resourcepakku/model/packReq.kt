package cc.mewcraft.resourcepakku.model

import net.kyori.adventure.text.Component

/**
 * 代表一个可以发送给玩家的资源包请求, 类似 Adventure 中的 [net.kyori.adventure.resource.ResourcePackRequest].
 *
 * @see PackInfo
 */
sealed interface PackRequest {

    companion object {

        @JvmStatic
        fun packRequest(
            packs: List<PackInfo>,
            force: Boolean,
            prompt: Component?,
        ): PackRequest = if (packs.isEmpty()) {
            EmptyPackRequest
        } else {
            SimplePackRequest(packs, force, prompt)
        }
    }

    val isEmpty: Boolean

    val packs: List<PackInfo>

    val force: Boolean

    val prompt: Component?
}

/**
 * 代表一个非空的资源包请求, 包含零个以上的资源包.
 *
 * @param packs 包含的资源包列表
 * @param force 是否强制客户端应用这些资源包
 * @param prompt 如果非空, 则在客户端显示该提示信息, 否则不显示任何提示信息
 */
private class SimplePackRequest(
    override val packs: List<PackInfo>,
    override val force: Boolean = false,
    override val prompt: Component? = null,
) : PackRequest {
    override val isEmpty: Boolean = false

    init {
        require(packs.isNotEmpty()) { "packs must not be empty" }
    }
}

/**
 * 代表一个空的资源包请求, 即不包含任何资源包.
 *
 * 当服务器没有定义任何资源包请求, 并且也没有定义默认的资源包请求时, 使用该实例.
 */
private data object EmptyPackRequest : PackRequest {
    override val isEmpty: Boolean = true
    override val packs: List<PackInfo> = emptyList()
    override val force: Boolean = false
    override val prompt: Component? = null
}
