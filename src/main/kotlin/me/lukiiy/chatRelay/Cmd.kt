package me.lukiiy.chatRelay

import com.mojang.brigadier.Command
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object Cmd {
    private val main = Commands.literal("chatrelay").requires { it.sender.hasPermission("chatrelay.cmd") }
        .then(Commands.literal("send")
            .then(Commands.argument("player", ArgumentTypes.player())
                .executes {
                    val target = it.getArgument("player", PlayerSelectorArgumentResolver::class.java).resolve(it.source).first().apply {
                        val instance = ChatRelay.getInstance()

                        if (instance.getMessages().isEmpty()) throw SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(Component.text("No message to relay.").color(NamedTextColor.RED))).create()
                        instance.showRelay(this)
                    }

                    it.source.sender.sendMessage(Component.text("Relayed the most recent logged messages to ").color(NamedTextColor.GREEN).append(target.name()))
                    Command.SINGLE_SUCCESS
                }
            )
        )
        .then(Commands.literal("reload")
            .executes {
                ChatRelay.getInstance().reloadConfig()
                it.source.sender.sendMessage(Component.text("ChatRelay Reload complete!").color(NamedTextColor.GREEN))

                Command.SINGLE_SUCCESS
            }
        )

    fun register(): LiteralCommandNode<CommandSourceStack> = main.build()
}