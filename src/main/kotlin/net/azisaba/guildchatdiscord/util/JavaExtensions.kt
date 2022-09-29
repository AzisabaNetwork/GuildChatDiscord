package net.azisaba.guildchatdiscord.util

fun <T, R> java.util.function.Function<T, R>.toKotlin(): Function1<T, R> = this::apply
