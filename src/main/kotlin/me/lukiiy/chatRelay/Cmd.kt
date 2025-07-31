package me.lukiiy.chatRelay

import com.mojang.brigadier.Command
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player

object Cmd {
    private val main = Commands.literal("chatrelay")
        .executes {
            val sender = it.source.sender as? Player ?: throw SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(Component.text("This command can only be used by in-game players.").color(NamedTextColor.RED))).create()
            val instance = ChatRelay.getInstance()

            if (instance.getMessages().isEmpty()) throw SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(Component.text("No message to relay.").color(NamedTextColor.RED))).create()
            instance.showRelay(sender)
            Command.SINGLE_SUCCESS
        }

    fun register(): LiteralCommandNode<CommandSourceStack> = main.build()
}