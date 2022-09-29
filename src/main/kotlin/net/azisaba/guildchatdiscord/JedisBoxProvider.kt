package net.azisaba.guildchatdiscord

import net.azisaba.interchat.api.network.JedisBox

object JedisBoxProvider {
    private lateinit var jedisBox: JedisBox

    fun get(): JedisBox {
        if (!(::jedisBox.isInitialized)) {
            jedisBox = BotConfig.instance.redis.createJedisBox()
        }
        return jedisBox
    }
}
