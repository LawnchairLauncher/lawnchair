package app.lawnchair.gestures

import android.content.Context
import android.os.UserHandle
import android.util.Log
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.util.kotlinxJson
import com.android.launcher3.shortcuts.ShortcutKey

/**
 * LC: the app shortcuts that gestures open. The launcher unpins every shortcut that is not on
 * the home screen; a pinned shortcut used only by a gesture would then stop working, so these
 * count as in use too.
 */
object GestureShortcutPins {

    @JvmStatic
    fun keysFor(context: Context, user: UserHandle): List<ShortcutKey> = try {
        PreferenceManager2.getInstance(context).getCachedPreferences().asMap().values.mapNotNull { value ->
            val json = value as? String ?: return@mapNotNull null
            if (!json.contains("shortcut")) return@mapNotNull null
            val config = runCatching { kotlinxJson.decodeFromString<GestureHandlerConfig>(json) }.getOrNull()
            val target = (config as? GestureHandlerConfig.OpenApp)?.target as? OpenAppTarget.Shortcut
            target?.takeIf { it.user == user }?.let { ShortcutKey(it.packageName, it.user, it.id) }
        }
    } catch (e: Exception) {
        Log.w("GestureShortcutPins", "Could not read gesture shortcuts", e)
        emptyList()
    }
}
