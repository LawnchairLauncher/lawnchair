package app.lawnchair.util.preferences

import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent

class PrefLifecycleObserver<T>(
    private val prefEntry: PrefEntry<T>,
    private val onChange: Consumer<T>
    ) : LifecycleObserver, PreferenceChangeListener {

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun connectListener() {
        prefEntry.addListener(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun disconnectListener() {
        prefEntry.removeListener(this)
    }

    override fun onPreferenceChange() {
        onChange.accept(prefEntry.get())
    }
}
