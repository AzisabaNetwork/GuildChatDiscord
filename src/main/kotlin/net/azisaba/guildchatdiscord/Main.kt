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
import net.azisaba.guildchatdiscord.commands.*
import net.azisaba.guildchatdiscord.util.DatabaseManager
import net.azisaba.interchat.api.InterChatProviderProvider
import net.azisaba.interchat.api.network.Protocol
import net.azisaba.interchat.api.network.protocol.GuildMessagePacket
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("GuildChatDiscord")

private val commandHandlers = mutableMapOf(
    "link" to LinkCommand,
    "unlink" to UnlinkCommand,
    "connect" to ConnectCommand,
    "unconnect" to UnconnectCommand,
    "nickname" to NickCommand,
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
        val guildId = DatabaseManager.getGuildIdByChannelId(message.channelId.value.toLong()) ?: return@on
        val minecraftUuid = DatabaseManager.getMinecraftUUIDByDiscordId(message.author!!.id.value.toLong()) ?: return@on
        // return if the author is not member of the guild
        InterChatDiscord.guildManager.getMember(guildId, minecraftUuid).exceptionally { null }.join() ?: return@on

        var content = message.content
        if (message.attachments.isNotEmpty()) content += "\n"
        message.attachments.forEach { content += "${it.url}\n" }
        // send packet
        val packet = GuildMessagePacket(guildId, "Discord", minecraftUuid, content.trim('\n'), null)
        JedisBoxProvider.get().pubSubHandler.publish(Protocol.GUILD_MESSAGE.name, packet)
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
