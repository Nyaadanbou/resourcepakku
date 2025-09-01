package cc.mewcraft.resourcepakku.util

import cc.mewcraft.resourcepakku.model.PackInfo

/**
 * 代表将要给玩家应用的资源包更改结果, 即最终要添加和移除的资源包.
 */
sealed interface ResourcePackChanges {

    companion object {

        /**
         * 计算资源包更改.
         *
         * @param preToApply 将要给玩家应用的资源包
         * @param preApplied 玩家已经应用的资源包
         * @return 资源包更改
         */
        @JvmStatic
        fun calculate(
            preToApply: List<PackInfo>,
            preApplied: List<PackInfo>,
        ): ResourcePackChanges {
            if (preToApply.isEmpty()) {
                // 计划上不需要应用任何资源包, 所以移除所有已经应用的资源包
                return Clear
            }

            if (preApplied == preToApply) {
                // 计划上将要应用的资源包和已经应用的资源包完全相同, 所以不需要执行任何操作, 让玩家直接进入服务器
                return NoOp
            }

            // 实际上要应用的资源包
            val finalToApply = preToApply.filter { it !in preApplied }
            // 实际上要移除的资源包
            val finalToRemove = preApplied.filter { it !in preToApply }

            return Normal(preToApply, finalToApply, finalToRemove)
        }
    }

    /**
     * 计划要添加的资源包列表.
     */
    val preToAdd: List<PackInfo>

    /**
     * 最终要添加的资源包列表.
     */
    val finalToAdd: List<PackInfo>

    /**
     * 最终要移除的资源包列表.
     */
    val finalToRemove: List<PackInfo>

    /**
     * 这种情况下, 不需要执行任何操作.
     */
    data object NoOp : ResourcePackChanges {
        override val preToAdd: List<PackInfo> get() = throw UnsupportedOperationException()
        override val finalToAdd: List<PackInfo> get() = throw UnsupportedOperationException()
        override val finalToRemove: List<PackInfo> get() = throw UnsupportedOperationException()
    }

    /**
     * 这种情况下, 所有已经应用的资源包都将被移除.
     */
    data object Clear : ResourcePackChanges {
        override val preToAdd: List<PackInfo> get() = throw UnsupportedOperationException()
        override val finalToAdd: List<PackInfo> get() = throw UnsupportedOperationException()
        override val finalToRemove: List<PackInfo> get() = throw UnsupportedOperationException()
    }

    /**
     * 这种情况下, 需要添加和移除一些资源包.
     */
    data class Normal(
        override val preToAdd: List<PackInfo>,
        override val finalToAdd: List<PackInfo>,
        override val finalToRemove: List<PackInfo>,
    ) : ResourcePackChanges {
        //init {
        //    require(toAdd.isNotEmpty() && toRemove.isNotEmpty()) { "toAdd and toRemove cannot be empty at the same time" }
        //}
    }
}