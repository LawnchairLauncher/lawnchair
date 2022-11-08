package app.lawnchair.preferences

import android.view.View
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import com.android.launcher3.util.SafeCloseable
import kotlin.reflect.KProperty

typealias ChangeListener = () -> Unit

sealed interface PrefEntry<T> {
    val key: String
    val defaultValue: T

    fun get(): T
    fun set(newValue: T)

    fun addListener(listener: PreferenceChangeListener)
    fun removeListener(listener: PreferenceChangeListener)

    fun subscribeChanges(onChange: Runnable): SafeCloseable {
        val observer = PrefLifecycleObserver(this, onChange)
        observer.connectListener()
        return SafeCloseable { observer.disconnectListener() }
    }

    fun subscribeChanges(lifecycleOwner: LifecycleOwner, onChange: Runnable) {
        lifecycleOwner.lifecycle.addObserver(PrefLifecycleObserver(this, onChange))
    }

    fun subscribeChanges(view: View, onChange: Runnable) {
        val observer = PrefLifecycleObserver(this, onChange)
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                observer.connectListener()
            }

            override fun onViewDetachedFromWindow(v: View) {
                observer.disconnectListener()
            }
        })
    }

    fun subscribeValues(lifecycleOwner: LifecycleOwner, onChange: Consumer<T>) {
        onChange.accept(get())
        subscribeChanges(lifecycleOwner) {
            onChange.accept(get())
        }
    }

    fun subscribeValues(view: View, onChange: Consumer<T>) {
        onChange.accept(get())
        subscribeChanges(view) {
            onChange.accept(get())
        }
    }

    operator fun getValue(thisObj: Any?, property: KProperty<*>): T = get()
    operator fun setValue(thisObj: Any?, property: KProperty<*>, newValue: T) {
        set(newValue)
    }
}
