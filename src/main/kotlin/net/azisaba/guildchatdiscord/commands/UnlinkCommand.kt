package net.azisaba.guildchatdiscord.commands

import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import net.azisaba.guildchatdiscord.getMinecraftIdName
import net.azisaba.guildchatdiscord.util.DatabaseManager

object UnlinkCommand : CommandHandler {
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val (uuid, name) = interaction.user.getMinecraftIdName() ?: run {
            interaction.respondEphemeral { content = "このアカウントは連携されていません。" }
            return
        }
        DatabaseManager.query("DELETE FROM `users` WHERE `discord_id` = ?") { stmt ->
            stmt.setLong(1, interaction.user.id.value.toLong())
            stmt.executeUpdate()
        }
        interaction.respondEphemeral { content = "`$name` (`$uuid`)の連携を解除しました。" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("unlink", "Minecraftアカウントの連携を解除します")
    }
}
