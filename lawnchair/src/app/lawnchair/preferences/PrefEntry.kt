package app.lawnchair.preferences

import android.view.View
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

    fun subscribe(lifecycleOwner: LifecycleOwner, fireOnAttach: Boolean = true, onChange: Consumer<T>) {
        lifecycleOwner.lifecycle.addObserver(PrefLifecycleObserver(this, fireOnAttach, onChange))
    }

    fun subscribe(view: View, fireOnAttach: Boolean = true, onChange: Consumer<T>) {
        val observer = PrefLifecycleObserver(this, fireOnAttach, onChange)
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                observer.connectListener()
            }

            override fun onViewDetachedFromWindow(v: View) {
                observer.disconnectListener()
            }
        })
    }

    operator fun getValue(thisObj: Any?, property: KProperty<*>): T = get()
    operator fun setValue(thisObj: Any?, property: KProperty<*>, newValue: T) {
        set(newValue)
    }
}
