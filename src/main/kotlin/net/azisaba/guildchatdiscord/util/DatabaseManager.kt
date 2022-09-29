package net.azisaba.guildchatdiscord.util

import net.azisaba.guildchatdiscord.BotConfig
import net.azisaba.interchat.api.util.Functions
import net.azisaba.interchat.api.util.QueryExecutor
import org.intellij.lang.annotations.Language
import java.sql.PreparedStatement

object DatabaseManager {
    val interChatQueryExecutor = QueryExecutor { sql, action -> queryInterChat(sql) { stmt -> action.accept(stmt) } }
    val dataSource = BotConfig.instance.database.createDataSource()
    private val interChatDataSource = BotConfig.instance.interChatDatabase.createDataSource()

    init {
        query("""
            CREATE TABLE IF NOT EXISTS `users` (
                `id` BIGINT NOT NULL AUTO_INCREMENT,
                `discord_id` BIGINT NOT NULL UNIQUE DEFAULT -1,
                `minecraft_uuid` VARCHAR(36) NOT NULL UNIQUE,
                `minecraft_name` VARCHAR(16) NOT NULL,
                `link_code` VARCHAR(8) NOT NULL UNIQUE,
                PRIMARY KEY (`id`)
            )
        """.trimIndent()) { it.executeUpdate() }
        query("""
            CREATE TABLE IF NOT EXISTS `channels` (
                `guild_id` BIGINT NOT NULL,
                `channel_id` BIGINT NOT NULL UNIQUE,
                `webhook_id` BIGINT NOT NULL,
                `webhook_token` VARCHAR(128) NOT NULL,
                UNIQUE KEY `webhook` (`webhook_id`, `webhook_token`)
            )
        """.trimIndent()) { it.executeUpdate() }
    }

    val getWebhooksByGuildId = Functions.memoize<Long, List<Pair<Long, String>>>(1000 * 60) { guildId ->
        query("SELECT `webhook_id`, `webhook_token` FROM `channels` WHERE `guild_id` = ?") {
            it.setLong(1, guildId)
            it.executeQuery().use { rs ->
                val list = mutableListOf<Pair<Long, String>>()
                while (rs.next()) {
                    list.add(rs.getLong("webhook_id") to rs.getString("webhook_token"))
                }
                list
            }
        }
    }.toKotlin()

    inline fun <R> query(@Language("SQL") sql: String, block: (PreparedStatement) -> R): R =
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use(block)
        }

    fun shutdown() {
        dataSource.close()
        interChatDataSource.close()
    }

    private inline fun queryInterChat(@Language("SQL") sql: String, block: (PreparedStatement) -> Unit) {
        interChatDataSource.connection.use { connection ->
            connection.prepareStatement(sql).use(block)
        }
    }
}
