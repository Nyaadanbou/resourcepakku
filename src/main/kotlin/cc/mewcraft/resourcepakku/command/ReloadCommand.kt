package cc.mewcraft.resourcepakku.command

import cc.mewcraft.resourcepakku.logger
import cc.mewcraft.resourcepakku.plugin
import com.velocitypowered.api.command.SimpleCommand
import com.velocitypowered.api.proxy.ConsoleCommandSource
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.time.measureTime

class ReloadCommand : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission("resourcepakku.reload")) {
            source.sendMessage(Component.text("No permission.").color(NamedTextColor.RED))
            return
        }

        val measureTime = measureTime {
            plugin.reloadPlugin()
        }

        val msg = "Plugin has been reloaded (errors are shown above). Took ${measureTime.inWholeMilliseconds}ms."
        logger.info(msg)
        if (source !is ConsoleCommandSource) {
            source.sendMessage(Component.text(msg).color(NamedTextColor.GREEN))
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        return listOf("reload")
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return invocation.source().hasPermission("resourcepakku.reload")
    }
}
