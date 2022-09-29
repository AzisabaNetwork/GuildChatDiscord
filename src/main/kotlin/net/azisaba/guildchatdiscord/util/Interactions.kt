package net.azisaba.guildchatdiscord.util

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.interaction.Interaction

fun Interaction.optAny(name: String): Any? =
    this.data
        .data
        .options
        .value
        ?.find { it.name == name }
        ?.value
        ?.value
        ?.value

fun Interaction.optString(name: String) = optAny(name) as String?

fun Interaction.optSnowflake(name: String) = optAny(name) as Snowflake?

fun Interaction.optLong(name: String) = optAny(name) as Long?
