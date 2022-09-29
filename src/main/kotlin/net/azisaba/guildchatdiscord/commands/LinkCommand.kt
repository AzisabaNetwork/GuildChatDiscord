package net.azisaba.guildchatdiscord.commands

import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import net.azisaba.guildchatdiscord.util.DatabaseManager
import net.azisaba.guildchatdiscord.util.optString

object LinkCommand : CommandHandler {
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val code = interaction.optString("code")!!
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
            interaction.respondEphemeral { content = "コードが無効です。" }
            return
        }
        val discordId = DatabaseManager.query("SELECT `discord_id` FROM `users` WHERE `link_code` = ?") { stmt ->
            stmt.setString(1, code)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    rs.getLong("discord_id")
                } else {
                    -1L
                }
            }
        }
        if (discordId != -1L) {
            // already linked
            interaction.respondEphemeral { content = "コードが無効です。" }
            return
        }
        val (uuid, name) = uuidName
        DatabaseManager.query("UPDATE `users` SET `discord_id` = ? WHERE `link_code` = ?") { stmt ->
            stmt.setLong(1, interaction.user.id.value.toLong())
            stmt.setString(2, code)
            stmt.executeUpdate()
        }
        interaction.respondEphemeral { content = "`$name` (`$uuid`)と連携しました。" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("link", "Link the Minecraft account") {
            string("code", "Code that you got from the Minecraft server") {
                required = true
            }
        }
    }
}
