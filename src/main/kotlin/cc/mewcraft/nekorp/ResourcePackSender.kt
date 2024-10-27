package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.config.PackConfig
import cc.mewcraft.nekorp.config.PackConfigs
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.resource.ResourcePackRequest

class ResourcePackSender(
    private val toApply: PackConfigs,
    private val playerAppliedResourcePacks: PackConfigs,
) {
    fun getChangeResult(): ChangeResult {
        val changes = mutableListOf<Change>()
        if (toApply.isEmpty()) {
            return ChangeResult.NoApply
        }

        for (pack in toApply) {
            if (pack !in playerAppliedResourcePacks) {
                changes.add(Change(Change.Type.ADD, pack))
            }
        }
        for (pack in playerAppliedResourcePacks) {
            if (pack !in toApply) {
                changes.add(Change(Change.Type.REMOVE, pack))
            }
        }

        return ChangeResult.Success(changes)
    }

    sealed interface ChangeResult {

        val changeSize: Int

        /**
         * The target server has changes to apply.
         */
        data class Success(val changes: List<Change>) : ChangeResult {
            init {
                require(changes.isNotEmpty()) { "changes must not be empty" }
            }

            override val changeSize: Int
                get() = changes.size
        }

        /**
         * The target server has no changes to apply.
         */
        data object NoChange : ChangeResult {
            override val changeSize: Int = 0
        }

        /**
         * The target server has no resource pack to apply.
         */
        data object NoApply : ChangeResult {
            override val changeSize: Int = 0
        }
    }


    data class Change(
        val type: Type,
        val pack: PackConfig,
    ) {
        enum class Type {
            ADD,
            REMOVE,
        }

        fun apply(player: Player, builder: ResourcePackRequest.Builder) {
            val info = pack.getResourcePackInfo(player.uniqueId, player.remoteAddress.address) ?: return
            when (type) {
                Type.ADD -> player.sendResourcePacks(builder.packs(info))
                Type.REMOVE -> player.removeResourcePacks(pack.uniqueId)
            }
        }
    }
}