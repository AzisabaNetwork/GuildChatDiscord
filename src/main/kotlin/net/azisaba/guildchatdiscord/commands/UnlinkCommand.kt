package net.azisaba.guildchatdiscord.commands

import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import net.azisaba.guildchatdiscord.util.DatabaseManager

object UnlinkCommand : CommandHandler {
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val uuidName = DatabaseManager.query("SELECT `minecraft_uuid`, `minecraft_name` FROM `users` WHERE `discord_id` = ?") { stmt ->
            stmt.setLong(1, interaction.user.id.value.toLong())
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    Pair(rs.getString("minecraft_uuid"), rs.getString("minecraft_name"))
                } else {
                    null
                }
            }
        }
        if (uuidName == null) {
            interaction.respondEphemeral { content = "このアカウントは連携されていません。" }
            return
        }
        val (uuid, name) = uuidName
        DatabaseManager.query("DELETE FROM `users` WHERE `discord_id` = ?") { stmt ->
            stmt.setLong(1, interaction.user.id.value.toLong())
            stmt.executeUpdate()
        }
        interaction.respondEphemeral { content = "`$name` (`$uuid`)の連携を解除しました。" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("unlink", "Unlink the Minecraft account")
    }
}
