@file:JvmName("MainKt")
package net.azisaba.guildchatdiscord

import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ApplicationCommandInteractionCreateEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import net.azisaba.guildchatdiscord.commands.ConnectCommand
import net.azisaba.guildchatdiscord.commands.LinkCommand
import net.azisaba.guildchatdiscord.commands.UnconnectCommand
import net.azisaba.guildchatdiscord.commands.UnlinkCommand
import net.azisaba.guildchatdiscord.util.DatabaseManager
import net.azisaba.interchat.api.InterChatProviderProvider
import net.azisaba.interchat.api.network.Protocol
import net.azisaba.interchat.api.network.protocol.GuildMessagePacket
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

private val logger = LoggerFactory.getLogger("GuildChatDiscord")

private val commandHandlers = mutableMapOf(
    "link" to LinkCommand,
    "unlink" to UnlinkCommand,
    "connect" to ConnectCommand,
    "unconnect" to UnconnectCommand,
)

@OptIn(PrivilegedIntent::class)
suspend fun main() {
    BotConfig.loadConfig(File("."))
    logger.info("Connecting to Redis")
    JedisBoxProvider.get()
    logger.info("Connecting to database")
    DatabaseManager

    InterChatProviderProvider.register(InterChatDiscord)

    val client = Kord(BotConfig.instance.token)

    client.createGlobalApplicationCommands {
        commandHandlers.values.forEach { it.register(this) }
    }

    client.on<MessageCreateEvent> {
        if (message.author?.isBot != false) return@on
        InterChatDiscord.asyncExecutor.execute {
            val minecraftUuid = DatabaseManager.query("SELECT `minecraft_uuid` FROM `users` WHERE `discord_id` = ?") {
                it.setLong(1, message.author!!.id.value.toLong())
                it.executeQuery().use { rs ->
                    if (rs.next()) {
                        UUID.fromString(rs.getString("minecraft_uuid"))
                    } else {
                        null
                    }
                }
            } ?: return@execute
            val guildId = DatabaseManager.query("SELECT `guild_id` FROM `channels` WHERE `channel_id` = ?") {
                it.setLong(1, message.channelId.value.toLong())
                it.executeQuery().use { rs ->
                    if (rs.next()) {
                        rs.getLong("guild_id")
                    } else {
                        null
                    }
                }
            } ?: return@execute
            val isMember = InterChatDiscord.guildManager.getMember(guildId, minecraftUuid).exceptionally { null }.join() != null
            if (!isMember) return@execute
            val packet = GuildMessagePacket(guildId, "Discord", minecraftUuid, message.content, null)
            JedisBoxProvider.get().pubSubHandler.publish(Protocol.GUILD_MESSAGE.name, packet)
        }
    }

    client.on<ApplicationCommandInteractionCreateEvent> {
        if (interaction.user.isBot) return@on // Bots cannot use commands
        commandHandlers[interaction.invokedCommandName]?.handle(interaction)
    }

    client.on<ReadyEvent> {
        logger.info("Logged in as ${kord.getSelf().tag}!")
    }

    client.login {
        intents = Intents(
            Intent.DirectMessages,
            Intent.MessageContent, // Privileged intent
            Intent.GuildMessages,
        )
    }

    // After logout/shutdown
    JedisBoxProvider.get().close()
    DatabaseManager.shutdown()
}
