package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.qsb.LawnQsbLayout

class OpenSearchGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val prefs = PreferenceManager2.getInstance(launcher)
        val searchProvider = LawnQsbLayout.getSearchProvider(launcher, prefs)
        searchProvider.launch(launcher)
    }
}
