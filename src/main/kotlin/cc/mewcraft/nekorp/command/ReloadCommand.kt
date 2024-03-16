package cc.mewcraft.nekorp.command

import cc.mewcraft.nekorp.util.plugin
import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component
import kotlin.time.measureTime

class ReloadCommand : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission("nekorp.reload")) {
            source.sendMessage(Component.text("You do not have permission to use this command!"))
            return
        }

        val measureTime = measureTime {
            plugin.reload()
        }

        source.sendMessage(Component.text("NekoRp has been reloaded. Took ${measureTime}ms."))
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        return listOf("reload")
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return invocation.source().hasPermission("nekorp.reload")
    }
}
