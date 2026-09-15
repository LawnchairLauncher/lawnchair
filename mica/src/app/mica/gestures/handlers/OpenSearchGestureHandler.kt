package app.mica.gestures.handlers

import android.content.Context
import app.mica.MicaLauncher
import app.mica.preferences2.PreferenceManager2
import app.mica.qsb.MicaQsbLayout

class OpenSearchGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: MicaLauncher) {
        val prefs = PreferenceManager2.getInstance(launcher)
        val searchProvider = MicaQsbLayout.getSearchProvider(launcher, prefs)
        searchProvider.launch(launcher)
    }
}
