package app.lawnchair.util.preferences

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent

class PrefLifecycleObserver<T>(
    private val prefEntry: PrefEntry<T>,
    private val onChange: () -> Unit
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
        onChange()
    }
}

fun <T> PrefEntry<T>.subscribe(lifecycleOwner: LifecycleOwner, onChange: () -> Unit) {
    lifecycleOwner.lifecycle.addObserver(PrefLifecycleObserver(this, onChange))
}
