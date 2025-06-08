package me.lukiiy.chatRelay

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.util.*
import java.util.function.Consumer


class ChatRelay : JavaPlugin(), Listener {
    private val messages: MutableList<String> = LinkedList()
    private lateinit var logFile: File
    private val mini = MiniMessage.miniMessage()

    var linePredicate: (String) -> Boolean = { true } // For plugins

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)

        setupConfig()
        logFile = File(dataFolder, "chat.log")

        loadLog()
    }

    override fun onDisable() = saveLog()

    companion object {
        fun getInstance(): ChatRelay = getPlugin(ChatRelay::class.java)
    }

    fun relay(player: Player) {
        val header = config.getString("msg.header")
        val footer = config.getString("msg.footer")
        val noHistory = config.getString("msg.noHistory")

        if (messages.isEmpty()) {
            if (!noHistory.isNullOrEmpty()) player.sendMessage(mini.deserialize(noHistory))
            return
        }

        synchronized(messages) {
            if (!header.isNullOrEmpty()) player.sendMessage(mini.deserialize(header))

            messages.forEach { player.sendMessage(mini.deserialize(it)) }

            if (!footer.isNullOrEmpty()) player.sendMessage(mini.deserialize(footer))
        }
    }

    // Config
    fun setupConfig() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()
    }

    // File
    private fun saveLog() = try {
        BufferedWriter(FileWriter(logFile)).use { writer ->
            for (msg in messages) {
                writer.write(msg)
                writer.newLine()
            }
        }
    } catch (e: Exception) { logger.warning(e.message) }

    private fun loadLog() {
        if (!logFile.exists()) return

        try {
            val lines = Files.readAllLines(logFile.toPath())
            if (lines.size > config.getInt("limit")) messages.addAll(lines.takeLast(config.getInt("limit"))) else messages.addAll(lines)
        } catch (e: Exception) { logger.warning(e.message) }
    }

    // Listener
    @EventHandler
    fun chat(e: AsyncChatEvent) {
        val p = e.player
        val name = mini.serialize(p.displayName())
        val msg = mini.serialize(e.message())

        if (!linePredicate(msg) || (config.getBoolean("requirePermission.save") && !p.hasPermission("chatrelay.save"))) return

        synchronized(messages) {
            messages.add(String.format(config.getString("msg.format") ?: "%s: %s", name, msg))
            if (messages.size > config.getInt("limit")) messages.removeAt(0)
            saveLog()
        }
    }

    @EventHandler
    fun join(e: PlayerJoinEvent) {
        val p = e.player
        if (config.getBoolean("requirePermission.display") && !p.hasPermission("chatrelay.see")) return

        p.scheduler.runDelayed(this, { relay(p) }, null, config.getLong("delay", 1).coerceAtLeast(1))
    }
}
