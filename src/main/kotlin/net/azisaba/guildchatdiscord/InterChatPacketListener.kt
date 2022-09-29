package net.azisaba.guildchatdiscord

import dev.kord.common.entity.Snowflake
import dev.kord.rest.request.RestRequestException
import dev.kord.rest.service.RestClient
import kotlinx.coroutines.runBlocking
import net.azisaba.guildchatdiscord.util.DatabaseManager
import net.azisaba.interchat.api.network.PacketListener
import net.azisaba.interchat.api.network.protocol.GuildMessagePacket
import net.azisaba.interchat.api.text.MessageFormatter
import net.azisaba.interchat.api.util.AsyncUtil
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

object InterChatPacketListener : PacketListener {
    private val restClient = RestClient(BotConfig.instance.token)
    private val LEGACY_COMPONENT_SERIALIZER = LegacyComponentSerializer.builder()
        .character('&')
        .extractUrls()
        .hexColors()
        .build()
    private val PLAIN_TEXT_COMPONENT_SERIALIZER = PlainTextComponentSerializer.plainText()

    override fun handleGuildMessage(packet: GuildMessagePacket) {
        val guildFuture = InterChatDiscord.guildManager.fetchGuildById(packet.guildId())
        val userFuture = InterChatDiscord.userManager.fetchUser(packet.sender())
        AsyncUtil.collectAsync(guildFuture, userFuture) { guild, user ->
            if (guild == null || user == null || !guild.deleted()) {
                return@collectAsync
            }
            val formattedText = MessageFormatter.format(
                guild.format(),
                guild,
                packet.server(),
                user,
                packet.message(),
                packet.transliteratedMessage(),
            )
            val formattedComponent = LEGACY_COMPONENT_SERIALIZER.deserialize(formattedText)
            val plainText = PLAIN_TEXT_COMPONENT_SERIALIZER.serialize(formattedComponent)
            DatabaseManager.getWebhooksByGuildId(packet.guildId()).forEach { (webhookId, webhookToken) ->
                InterChatDiscord.asyncExecutor.execute {
                    runBlocking {
                        try {
                            restClient.webhook.executeWebhook(Snowflake(webhookId.toULong()), webhookToken) {
                                content = plainText
                            }
                        } catch (e: RestRequestException) {
                            if (e.status.code == 404) {
                                // webhook is gone
                                DatabaseManager.query("DELETE FROM `channels` WHERE `webhook_id` = ?") {
                                    it.setLong(1, webhookId)
                                    it.executeUpdate()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
