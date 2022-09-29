package net.azisaba.guildchatdiscord

import net.azisaba.guildchatdiscord.util.DatabaseManager
import net.azisaba.interchat.api.InterChat
import net.azisaba.interchat.api.Logger
import net.azisaba.interchat.api.guild.GuildManager
import net.azisaba.interchat.api.guild.SQLGuildManager
import net.azisaba.interchat.api.user.SQLUserManager
import net.azisaba.interchat.api.user.UserManager
import org.slf4j.LoggerFactory
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