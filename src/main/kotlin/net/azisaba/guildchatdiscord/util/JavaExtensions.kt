package net.azisaba.guildchatdiscord.util

fun <T, R> java.util.function.Function<T, R>.toKotlin(): Function1<T, R> = this::apply

fun <T, R> java.util.function.Function<T, R>.toMemoizeFunction(): MemoizeFunction<T, R> = MemoizeFunction(this)

class MemoizeFunction<T, R>(private val f: java.util.function.Function<T, R>) : (T) -> R {
    override operator fun invoke(t: T): R = f.apply(t)

    fun forgetAll() = getMap().clear()

    fun forget(key: T) = getMap().remove(key)

    private fun getMap(): MutableMap<T, *> {
        @Suppress("UNCHECKED_CAST")
        return f::class.java.getDeclaredField("cache").apply { isAccessible = true }[f] as MutableMap<T, *>
    }
}
