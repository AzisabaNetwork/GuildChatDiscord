package net.azisaba.guildchatdiscord

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlComment
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.azisaba.interchat.api.Logger
import net.azisaba.interchat.api.network.JedisBox
import net.azisaba.interchat.api.network.Side
import org.mariadb.jdbc.Driver
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger(BotConfig::class.java)!!

@Serializable
data class BotConfig(
    val token: String = "BOT_TOKEN_HERE",
    val redis: RedisConfig = RedisConfig(),
    val database: DatabaseConfig = DatabaseConfig(),
    val interChatDatabase: InterChatDatabaseConfig = InterChatDatabaseConfig(),
) {
    companion object {
        lateinit var instance: BotConfig

        fun loadConfig(dataFolder: File) {
            val configFile = File(dataFolder, "config.yml")
            logger.info("Loading config from $configFile (absolute path: ${configFile.absolutePath})")
            if (!configFile.exists()) {
                logger.info("Config file not found. Creating new one.")
                configFile.writeText(Yaml.default.encodeToString(serializer(), BotConfig()) + "\n")
            }
            instance = Yaml.default.decodeFromStream(serializer(), configFile.inputStream())
            logger.info("Saving config to $configFile (absolute path: ${configFile.absolutePath})")
            configFile.writeText(Yaml.default.encodeToString(serializer(), instance) + "\n")

            Driver() // register driver here, just in case it's not registered yet.
        }
    }
}

@SerialName("redis")
@Serializable
data class RedisConfig(
    val hostname: String = "localhost",
    val port: Int = 6379,
    val username: String? = null,
    val password: String? = null,
) {
    fun createJedisBox() =
        JedisBox(
            Side.PROXY,
            Logger.createByProxy(logger),
            InterChatPacketListener,
            hostname,
            port,
            username,
            password,
        )
}

@Serializable
abstract class BaseDatabaseConfig(
    val driver: String = "net.azisaba.guildchatdiscord.lib.org.mariadb.jdbc.Driver",
    @YamlComment("Change to jdbc:mysql if you want to use MySQL instead of MariaDB")
    val scheme: String = "jdbc:mariadb",
    val hostname: String = "localhost",
    val port: Int = 3306,
    val username: String = "guildchatdiscord",
    val password: String = "",
    val properties: Map<String, String> = mapOf(
        "useSSL" to "false",
        "verifyServerCertificate" to "true",
        "prepStmtCacheSize" to "250",
        "prepStmtCacheSqlLimit" to "2048",
        "cachePrepStmts" to "true",
        "useServerPrepStmts" to "true",
        "socketTimeout" to "60000",
        "useLocalSessionState" to "true",
        "rewriteBatchedStatements" to "true",
        "maintainTimeStats" to "false",
    ),
) {
    abstract val name: String
    
    fun createDataSource(): HikariDataSource {
        val config = HikariConfig()
        val actualDriver = try {
            Class.forName(driver)
            driver
        } catch (e: ClassNotFoundException) {
            logger.warn("Failed to load driver class $driver. Falling back to org.mariadb.jdbc.Driver")
            "org.mariadb.jdbc.Driver"
        }
        config.driverClassName = actualDriver
        config.jdbcUrl = "$scheme://$hostname:$port/$name"
        config.username = username
        config.password = password
        config.dataSourceProperties = properties.toProperties()
        return HikariDataSource(config)
    }
}

@Serializable
data class DatabaseConfig(
    override val name: String = "guildchatdiscord",
) : BaseDatabaseConfig()

@Serializable
data class InterChatDatabaseConfig(
    override val name: String = "interchat",
) : BaseDatabaseConfig()
