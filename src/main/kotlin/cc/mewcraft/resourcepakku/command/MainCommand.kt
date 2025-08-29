package cc.mewcraft.resourcepakku.command

import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component

class MainCommand : SimpleCommand {

    private val subCommands: Map<String, SimpleCommand> = mapOf(
        "reload" to ReloadCommand()
    )

    override fun execute(invocation: SimpleCommand.Invocation) {
        val args = invocation.arguments()
        if (args.isNotEmpty()) {
            val subCommand = subCommands[args[0].lowercase()]
            if (subCommand != null) {
                subCommand.execute(invocation)
                return
            }
        }
        // 如果没有找到子命令，显示帮助信息或类似消息
        invocation.source().sendMessage(Component.text("Unknown command. Use /resourcepakku reload."))
    }

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> {
        return subCommands.keys.toList()
    }
}
