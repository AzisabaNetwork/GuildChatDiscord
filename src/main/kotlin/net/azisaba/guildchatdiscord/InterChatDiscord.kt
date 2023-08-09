package net.azisaba.guildchatdiscord

import dev.kord.core.entity.User
import net.azisaba.guildchatdiscord.util.DatabaseManager
import net.azisaba.interchat.api.InterChat
import net.azisaba.interchat.api.Logger
import net.azisaba.interchat.api.guild.GuildManager
import net.azisaba.interchat.api.guild.SQLGuildManager
import net.azisaba.interchat.api.user.SQLUserManager
import net.azisaba.interchat.api.user.UserManager
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object InterChatDiscord : InterChat {
    private val logger = Logger.createByProxy(LoggerFactory.getLogger("GuildChatDiscord"))
    private val guildManager = SQLGuildManager(DatabaseManager.interChatQueryExecutor)
    private val userManager = SQLUserManager(DatabaseManager.interChatQueryExecutor)
    private val asyncExecutor = Executors.newCachedThreadPool()

    override fun getLogger(): Logger = logger

    override fun getGuildManager(): GuildManager = guildManager

    override fun getUserManager(): UserManager = userManager

    override fun getAsyncExecutor(): Executor = asyncExecutor
}

fun User.getMinecraftIdName() =
    DatabaseManager.query("SELECT `minecraft_uuid`, `minecraft_name` FROM `users` WHERE `discord_id` = ?") { stmt ->
        stmt.setLong(1, id.value.toLong())
        stmt.executeQuery().use { rs ->
            if (rs.next()) {
                Pair(UUID.fromString(rs.getString("minecraft_uuid")), rs.getString("minecraft_name"))
            } else {
                null
            }
        }
    }
