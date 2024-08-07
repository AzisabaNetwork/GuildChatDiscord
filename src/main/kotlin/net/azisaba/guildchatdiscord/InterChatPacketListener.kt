package net.azisaba.guildchatdiscord

import dev.kord.common.entity.Snowflake
import dev.kord.rest.builder.message.AllowedMentionsBuilder
import dev.kord.rest.request.RestRequestException
import dev.kord.rest.service.RestClient
import kotlinx.coroutines.runBlocking
import net.azisaba.guildchatdiscord.util.DatabaseManager
import net.azisaba.interchat.api.data.PlayerPosData
import net.azisaba.interchat.api.data.SenderInfo
import net.azisaba.interchat.api.guild.GuildMember
import net.azisaba.interchat.api.network.PacketListener
import net.azisaba.interchat.api.network.RedisKeys
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
            if (guild == null || user == null || guild.deleted()) {
                return@collectAsync
            }
            val members = guild.members.join()
            val nickname = members.stream().filter { it.uuid() == user.id() }.findAny().map(GuildMember::nickname)
            val pos = try {
                JedisBoxProvider.get()[RedisKeys.azisabaReportPlayerPos(user.id()), PlayerPosData.NETWORK_CODEC, 1000].toWorldPos()
            } catch (_: Exception) {
                null
            }
            val senderInfo = SenderInfo(user, packet.server(), nickname.orElse(null), pos)
            val formattedText = MessageFormatter.format(
                guild.format(),
                guild,
                senderInfo,
                packet.message(),
                packet.transliteratedMessage(),
                emptyMap(), // TODO: support prefix and suffix
            )
            val formattedComponent = LEGACY_COMPONENT_SERIALIZER.deserialize(formattedText)
            val plainText = PLAIN_TEXT_COMPONENT_SERIALIZER.serialize(formattedComponent)
            DatabaseManager.getWebhooksByGuildId(packet.guildId()).forEach { info ->
                InterChatDiscord.asyncExecutor.execute {
                    runBlocking {
                        // make sure that member is still in the guild
                        val uuid = DatabaseManager.getMinecraftUUIDByDiscordId(info.createdUserId)
                        if (uuid == null || members.all { it.uuid() != uuid }) {
                            // delete webhook
                            InterChatDiscord.logger.info("Removing webhook ${info.webhookId} because the user is not in the guild")
                            try {
                                restClient.webhook.deleteWebhook(
                                    Snowflake(info.webhookId.toULong()),
                                    "${info.createdUserId} was removed from the guild"
                                )
                            } catch (e: Exception) {
                                InterChatDiscord.logger.info("Failed to delete webhook", e)
                            }
                            DatabaseManager.query("DELETE FROM `channels` WHERE `webhook_id` = ?") {
                                it.setLong(1, info.webhookId)
                                it.executeUpdate()
                            }
                            DatabaseManager.getWebhooksByGuildId.forget(packet.guildId())
                            return@runBlocking
                        }
                        // execute webhook
                        try {
                            restClient.webhook.executeWebhook(Snowflake(info.webhookId.toULong()), info.webhookToken) {
                                content = plainText
                                allowedMentions = AllowedMentionsBuilder()
                            }
                        } catch (e: RestRequestException) {
                            if (e.status.code == 404 || e.status.code == 403) {
                                // invalid webhook url?
                                InterChatDiscord.logger.info("Removing webhook ${info.webhookId} because it is invalid (404 or 403)")
                                DatabaseManager.query("DELETE FROM `channels` WHERE `webhook_id` = ?") {
                                    it.setLong(1, info.webhookId)
                                    it.executeUpdate()
                                }
                                DatabaseManager.getWebhooksByGuildId.forget(packet.guildId())
                            }
                        }
                    }
                }
            }
        }
    }
}
