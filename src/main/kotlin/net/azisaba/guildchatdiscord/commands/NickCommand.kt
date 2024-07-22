package net.azisaba.guildchatdiscord.commands

import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.core.kordLogger
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import net.azisaba.guildchatdiscord.InterChatDiscord
import net.azisaba.guildchatdiscord.getMinecraftIdName
import net.azisaba.guildchatdiscord.util.optString
import net.azisaba.interchat.api.guild.GuildMember

object NickCommand : CommandHandler {
    override suspend fun handle0(interaction: ApplicationCommandInteraction) {
        val guildName = interaction.optString("guild")!!
        val newName = interaction.optString("name")
        val (uuid) = interaction.user.getMinecraftIdName() ?: run {
            interaction.respondEphemeral { content = "Minecraftアカウントが連携されていません。" }
            return
        }
        val guild = try {
            InterChatDiscord.guildManager.fetchGuildByName(guildName).join()
        } catch (e: Exception) {
            interaction.respondEphemeral { content = "ギルドの取得に失敗しました。ギルドが存在しない可能性があります。" }
            kordLogger.info("Could not find guild by name $guildName", e)
            return
        }
        val oldMember = guild.getMember(uuid).join()
        val newMember = GuildMember(oldMember.guildId(), oldMember.uuid(), oldMember.role(), newName, oldMember.hiddenByMember())
        newMember.update()
        interaction.respondEphemeral { content = "ニックネームを `$newName` に変更しました。" }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("nickname", "ギルドでのニックネームを変更または削除します") {
            string("guild", "ギルドの名前") {
                required = true
            }
            string("name", "新しいニックネーム")
        }
    }
}
