package app.lawnchair.preferences

import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import kotlin.reflect.KProperty

typealias ChangeListener = () -> Unit

interface PrefEntry<T> {
    val defaultValue: T

    fun get(): T
    fun set(newValue: T)

    fun addListener(listener: PreferenceChangeListener)
    fun removeListener(listener: PreferenceChangeListener)

    fun subscribe(lifecycleOwner: LifecycleOwner, onChange: Consumer<T>) {
        lifecycleOwner.lifecycle.addObserver(PrefLifecycleObserver(this, onChange))
    }

    operator fun getValue(thisObj: Any?, property: KProperty<*>): T = get()
    operator fun setValue(thisObj: Any?, property: KProperty<*>, newValue: T) {
        set(newValue)
    }
}
