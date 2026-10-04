package app.lawnchair.util

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2

enum class FolderMotion {
    MORPH,
    GROW,
    INSTANT,
    ;

    companion object {
        @JvmStatic
        fun get(context: Context): FolderMotion {
            val prefs = PreferenceManager2.getInstance(context)
            return resolve(prefs.folderMotion.firstCached(prefs), prefs.reduceHomeScreenMotion.firstCached(prefs))
        }

        // Preserve the earlier combined motion setting until a folder mode is chosen explicitly.
        fun resolve(value: String, reducedHomeMotion: Boolean): FolderMotion = entries.firstOrNull { it.name == value } ?: if (reducedHomeMotion) INSTANT else MORPH
    }
}
