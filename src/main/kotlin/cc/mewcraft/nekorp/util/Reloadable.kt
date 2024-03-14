package cc.mewcraft.nekorp.util

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

fun <T> reloadable(loader: () -> T) = Reloadable(loader)

class Reloadable<T>(private val loader: () -> T) : ReadOnlyProperty<Any?, T> {
    private var value: T? = null

    init {
        // plugin.listen<TypewriterReloadEvent> { reload() }
    }

    fun get(): T {
        val value = this.value
        if (value == null) {
            val createdValue = loader()
            this.value = createdValue
            return createdValue
        }
        return value
    }

    private fun reload() {
        value = null
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return get()
    }
}