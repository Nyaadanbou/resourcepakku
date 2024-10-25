package cc.mewcraft.nekorp

import cc.mewcraft.nekorp.config.PackConfig
import cc.mewcraft.nekorp.config.PackConfigs
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.resource.ResourcePackRequest

class ResourcePackSender(
    private val toApply: PackConfigs,
    private val playerAppliedResourcePacks: PackConfigs,
) {
    fun getChanges(): List<Change> {
        val changes = mutableListOf<Change>()
        if (toApply.isEmpty()) {
            for (pack in playerAppliedResourcePacks) {
                changes.add(Change(Change.Type.REMOVE, pack))
            }
            return changes
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
        return changes
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