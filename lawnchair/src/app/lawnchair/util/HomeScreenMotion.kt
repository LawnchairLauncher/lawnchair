package app.lawnchair.util

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2

object HomeScreenMotion {
    @JvmStatic
    fun isReduced(context: Context): Boolean {
        val prefs = PreferenceManager2.getInstance(context)
        return prefs.reduceHomeScreenMotion.firstCached(prefs)
    }

    @JvmStatic
    fun wallpaperZoomEnabled(context: Context): Boolean {
        val prefs = PreferenceManager2.getInstance(context)
        return !isReduced(context) && prefs.wallpaperDepthEffect.firstCached(prefs)
    }
}
