package me.lukiiy.chatRelay

import io.papermc.paper.event.player.AsyncChatEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.audience.Audience
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

class ChatRelay : JavaPlugin(), Listener {
    private val messages: ConcurrentLinkedQueue<String> = ConcurrentLinkedQueue()
    private lateinit var logFile: File
    private val mini = MiniMessage.miniMessage()
    private var predicates: MutableList<(String) -> Boolean> = mutableListOf() // For plugins

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)

        setupConfig()
        logFile = File(dataFolder, "chat.log")

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            it.registrar().register(Cmd.register(), "Main command for ChatRelay")
        }

        loadLog()
    }

    override fun onDisable() = saveLog()

    companion object {
        fun getInstance(): ChatRelay = getPlugin(ChatRelay::class.java)
    }

    // Config
    fun setupConfig() {
        saveDefaultConfig()
        config.options().copyDefaults(true)
        saveConfig()
    }

    /**
     * Shows the last few saved messages to an audience
     * @param audience An [Audience], e.g. a [Player] object
     */
    fun showRelay(audience: Audience) {
        val header = config.getString("msg.header")
        val footer = config.getString("msg.footer")
        val noHistory = config.getString("msg.noHistory")
        val msgs = getMessages()

        if (msgs.isEmpty()) {
            if (!noHistory.isNullOrEmpty()) audience.sendMessage(mini.deserialize(noHistory))
            return
        }

        if (!header.isNullOrEmpty()) audience.sendMessage(mini.deserialize(header))
        msgs.forEach { audience.sendMessage(mini.deserialize(it)) }
        if (!footer.isNullOrEmpty()) audience.sendMessage(mini.deserialize(footer))
    }

    // File
    private fun saveLog() = try {
        BufferedWriter(FileWriter(logFile)).use {
            for (msg in messages) {
                it.write(msg)
                it.newLine()
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

    // API

    /**
     * Gets the saved messages from
     */
    fun getMessages(): List<String> = messages.toList()

    /**
     * Add conditions for the logger
     * @param condition A predicate
     */
    fun addCondition(condition: (String) -> Boolean) {
        predicates += condition
    }

    /**
     * Add conditions from the logger
     * @param condition A predicate
     */
    fun removeCondition(condition: (String) -> Boolean) {
        predicates -= condition
    }

    /**
     * Checks if a message complies with the registered conditions
     * @param message The message
     * @return True if the message complies, false otherwise
     */
    fun doesMessageComply(message: String): Boolean = predicates.all { it(message) }

    @JvmOverloads
    fun addMessageToRelay(message: String, complianceCheck: Boolean = true) {
        if (!complianceCheck || doesMessageComply(message)) {
            messages.add(message)
            while (messages.size > config.getInt("limit")) messages.poll()
        }
    }

    /**
     * Removes a message from the logs
     * @param message The message
     */
    fun removeMessageFromRelay(message: String) = messages.remove(message)

    // Echo
    @EventHandler
    private fun chat(e: AsyncChatEvent) {
        val p = e.player
        val msg = mini.serialize(e.message())

        if (!doesMessageComply(msg) || (config.getBoolean("requirePermission.save") && !p.hasPermission("chatrelay.save"))) return

        val format = config.getString("msg.format") ?: "<%p> %msg"
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern(config.getString("msg.timeFormat") ?: "HH:mm"))

        addMessageToRelay(format.replace("%time", time).replace("%p", mini.serialize(p.displayName())).replace("%msg", msg))
    }

    @EventHandler
    private fun join(e: PlayerJoinEvent) {
        val p = e.player

        if (config.getBoolean("requirePermission.display") && !p.hasPermission("chatrelay.see")) return

        p.scheduler.runDelayed(this, { showRelay(p) }, null, config.getLong("delay", 1).coerceAtLeast(1))
    }
}
