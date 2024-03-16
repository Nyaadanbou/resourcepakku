package cc.mewcraft.nekorp.util

import cc.mewcraft.nekorp.NekoRp
import com.velocitypowered.api.event.PostOrder

inline fun <reified T : Any> NekoRp.listen(
    order: PostOrder = PostOrder.NORMAL,
    noinline action: (T) -> Unit,
) {
    listen(T::class.java, order, action)
}