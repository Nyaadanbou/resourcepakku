package cc.mewcraft.nekorp

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.resource.ResourcePackInfoLike
import net.kyori.adventure.resource.ResourcePackRequest

class ResourcePackSender(
    private val toApply: Collection<ResourcePackInfoLike>,
    private val playerAppliedResourcePacks: Collection<ResourcePackInfoLike>,
) {
    fun getChanges(): Collection<Change> {
        val changes = mutableListOf<Change>()
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

    class Change(
        val type: Type,
        val pack: ResourcePackInfoLike,
    ) {
        enum class Type {
            ADD,
            REMOVE,
        }

        fun apply(player: Player, builder: ResourcePackRequest.Builder) {
            when (type) {
                Type.ADD -> player.sendResourcePacks(builder.packs(pack))
                Type.REMOVE -> player.removeResourcePacks(builder.packs(pack))
            }
        }
    }
}