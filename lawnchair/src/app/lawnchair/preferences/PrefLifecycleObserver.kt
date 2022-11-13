package app.lawnchair.preferences

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class PrefLifecycleObserver<T>(
    private val prefEntry: PrefEntry<T>,
    private val onChange: Runnable
) : DefaultLifecycleObserver, PreferenceChangeListener {
    
    fun connectListener() {
        prefEntry.addListener(this)
    }

    fun disconnectListener() {
        prefEntry.removeListener(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        connectListener()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        disconnectListener()
    }

    override fun onPreferenceChange() {
        onChange.run()
    }
}
