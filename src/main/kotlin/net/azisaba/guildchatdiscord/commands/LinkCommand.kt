package net.azisaba.guildchatdiscord.commands

import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import net.azisaba.guildchatdiscord.util.DatabaseManager
import net.azisaba.guildchatdiscord.util.optString

object LinkCommand : CommandHandler {
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val defer = interaction.deferEphemeralResponse()
        val code = interaction.optString("code")!!
        val linked = DatabaseManager.query("SELECT `minecraft_uuid`, `minecraft_name` FROM `users` WHERE `discord_id` = ?") { stmt ->
            stmt.setString(1, interaction.user.id.toString())
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    Pair(rs.getString("minecraft_uuid"), rs.getString("minecraft_name"))
                } else {
                    null
                }
            }
        }
        if (linked != null) {
            defer.respond {
                content = "このDiscordアカウントはすでに${linked.second} (`${linked.first}`)と連携済みです。\n連携を解除するには`/unlink`を入力してください。"
            }
            return
        }
        val uuidName = DatabaseManager.query("SELECT `minecraft_uuid`, `minecraft_name` FROM `users` WHERE `link_code` = ?") { stmt ->
            stmt.setString(1, code)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    Pair(rs.getString("minecraft_uuid"), rs.getString("minecraft_name"))
                } else {
                    null
                }
            }
        }
        if (uuidName == null) {
            defer.respond { content = "コードが無効です。" }
            return
        }
        val discordIdAndExpiresAt = DatabaseManager.query("SELECT `discord_id`, `expires_at` FROM `users` WHERE `link_code` = ?") { stmt ->
            stmt.setString(1, code)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    Pair(rs.getLong("discord_id"), rs.getLong("expires_at"))
                } else {
                    Pair(-1L, 0L)
                }
            }
        }
        if (discordIdAndExpiresAt.first != -1L || discordIdAndExpiresAt.second < System.currentTimeMillis()) {
            // already linked or expired
            defer.respond { content = "コードが無効です。" }
            return
        }
        val (uuid, name) = uuidName
        DatabaseManager.query("UPDATE `users` SET `discord_id` = ? WHERE `link_code` = ?") { stmt ->
            stmt.setLong(1, interaction.user.id.value.toLong())
            stmt.setString(2, code)
            stmt.executeUpdate()
        }
        DatabaseManager.getMinecraftUUIDByDiscordId.forget(interaction.user.id.value.toLong())
        defer.respond { content = "`$name` (`$uuid`)と連携しました。" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("link", "Minecraftアカウントを連携します") {
            string("code", "サーバー内から入手したコード") {
                required = true
            }
        }
    }
}
