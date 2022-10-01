package net.azisaba.guildchatdiscord.commands

import dev.kord.common.entity.Permission
import dev.kord.common.entity.Permissions
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import net.azisaba.guildchatdiscord.InterChatDiscord
import net.azisaba.guildchatdiscord.util.DatabaseManager
import net.azisaba.guildchatdiscord.util.optString
import net.azisaba.interchat.api.user.User
import java.util.UUID

object ConnectCommand : CommandHandler {
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val defer = interaction.deferPublicResponse()
        val channel = interaction.channel.asChannel() as TopGuildChannel
        if (!channel.getEffectivePermissions(interaction.kord.selfId).contains(Permission.ManageWebhooks)) {
            defer.respond { content = "BotがこのチャンネルにWebhookを作成する権限がありません。" }
            return
        }
        val minecraftUuid = DatabaseManager.query("SELECT `minecraft_uuid` FROM `users` WHERE `discord_id` = ?") {
            it.setLong(1, interaction.user.id.value.toLong())
            it.executeQuery().use { rs ->
                if (rs.next()) {
                    UUID.fromString(rs.getString("minecraft_uuid"))
                } else {
                    null
                }
            }
        }
        if (minecraftUuid == null) {
            defer.respond { content = "Minecraftアカウントと連携されていません。" }
            return
        }
        val user: User = InterChatDiscord.userManager.fetchUser(minecraftUuid).join()
        val guildName = interaction.optString("guild")!!
        val guild = InterChatDiscord.guildManager.fetchGuildByName(guildName).exceptionally { null }.join()
        if (guild == null || guild.deleted()) {
            defer.respond { content = "ギルド`$guildName`に参加していません。" }
            return
        }
        if (guild.getMember(user).exceptionally { null }.join() == null) {
            defer.respond { content = "ギルド`$guildName`に参加していません。" }
            return
        }
        val linkedGuildId = DatabaseManager.query("SELECT `guild_id` FROM `channels` WHERE `channel_id` = ?") {
            it.setLong(1, channel.id.value.toLong())
            it.executeQuery().use { rs ->
                if (rs.next()) {
                    rs.getLong("guild_id")
                } else {
                    -1L
                }
            }
        }
        if (linkedGuildId != -1L) {
            val linkedGuild = InterChatDiscord.guildManager.fetchGuildById(linkedGuildId).exceptionally { null }.join()
            if (linkedGuild != null && !linkedGuild.deleted()) {
                defer.respond {
                    content = "このチャンネルは既にギルド`${linkedGuild.name()}`と連携されています。\n`/unconnect`で連携を解除できます。"
                }
            } else {
                defer.respond { content = "`/unconnect`を実行して連携を解除してからもう一度お試しください。" }
            }
            return
        }
        val webhook = channel.kord.rest.webhook.createWebhook(channel.id, "ギルドチャット (${guild.name()})") {
            reason = "/connect command from ${interaction.user.tag} (${interaction.user.id})"
        }
        DatabaseManager.query("INSERT INTO `channels` (`guild_id`, `channel_id`, `webhook_id`, `webhook_token`, `created_user_id`) VALUES (?, ?, ?, ?, ?)") {
            it.setString(1, guild.id().toString())
            it.setLong(2, channel.id.value.toLong())
            it.setLong(3, webhook.id.value.toLong())
            it.setString(4, webhook.token.value)
            it.setLong(5, interaction.user.id.value.toLong())
            it.executeUpdate()
        }
        DatabaseManager.getGuildIdByChannelId.forget(channel.id.value.toLong())
        DatabaseManager.getWebhooksByGuildId.forget(guild.id())
        defer.respond { content = "ギルドチャットを`${guild.name()}`と連携しました。\nメッセージが受信できるようになるまで最大60秒程度かかります。" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("connect", "Connect the guild chat") {
            dmPermission = false
            defaultMemberPermissions = Permissions(Permission.ManageWebhooks)
            string("guild", "Guild name") {
                required = true
            }
        }
    }
}
