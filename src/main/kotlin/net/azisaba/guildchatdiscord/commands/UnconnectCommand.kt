package net.azisaba.guildchatdiscord.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import net.azisaba.guildchatdiscord.InterChatDiscord
import net.azisaba.guildchatdiscord.util.DatabaseManager

object UnconnectCommand : CommandHandler {
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val triple = DatabaseManager.query("SELECT `guild_id`, `webhook_id`, `webhook_token` FROM `channels` WHERE `channel_id` = ?") {
            it.setLong(1, interaction.channelId.value.toLong())
            it.executeQuery().use { rs ->
                if (rs.next()) {
                    Triple(
                        rs.getLong("guild_id"),
                        Snowflake(rs.getLong("webhook_id").toULong()),
                        rs.getString("webhook_token"),
                    )
                } else {
                    null
                }
            }
        }
        if (triple == null) {
            interaction.respondPublic { content = "このチャンネルは連携されていません。" }
            return
        }
        val (guildId, webhookId, webhookToken) = triple
        interaction
            .kord
            .rest
            .webhook
            .deleteWebhookWithToken(webhookId, webhookToken, "/unconnect command from ${interaction.user.tag}")
        DatabaseManager.query("DELETE FROM `channels` WHERE `channel_id` = ?") {
            it.setLong(1, interaction.channelId.value.toLong())
            it.executeUpdate()
        }
        val guild = InterChatDiscord.guildManager.fetchGuildById(guildId).exceptionally { null }.join()
        if (guild == null || guild.deleted()) {
            interaction.respondPublic { content = "連携を解除しました。" }
        } else {
            interaction.respondPublic { content = "このチャンネルに連携されていたギルド`${guild.name()}`の連携を解除しました。" }
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("unconnect", "Removes the integration with the guild chat.") {
            dmPermission = false
            defaultMemberPermissions = Permissions(Permission.ManageWebhooks)
        }
    }
}