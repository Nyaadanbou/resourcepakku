package cc.mewcraft.resourcepakku.util

import cc.mewcraft.resourcepakku.ResourcePakkuPlugin
import com.velocitypowered.api.event.PostOrder

inline fun <reified T : Any> ResourcePakkuPlugin.listen(
    order: PostOrder = PostOrder.NORMAL,
    noinline action: (T) -> Unit,
) {
    listen(T::class.java, order, action)
}