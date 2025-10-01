package cc.mewcraft.resourcepakku.command

import cc.mewcraft.resourcepakku.plugin
import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class ResendCommand : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        if (!source.hasPermission("resourcepakku.resend")) {
            source.sendMessage(Component.text("No permission.").color(NamedTextColor.RED))
            return
        }

        source.sendMessage(Component.text("Resending packs to all connected clients..."))

        plugin.packController.resend().thenRun {
            source.sendMessage(Component.text("Packs are resent to all connected clients."))
        }
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        return listOf("resend")
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean {
        return invocation.source().hasPermission("resourcepakku.resend")
    }
}