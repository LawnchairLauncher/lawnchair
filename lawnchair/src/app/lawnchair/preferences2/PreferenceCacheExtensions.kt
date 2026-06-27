package app.lawnchair.preferences2

import app.lawnchair.LawnchairApp
import com.patrykmichalik.opto.core.PreferenceImpl
import com.patrykmichalik.opto.core.getFromPreferences
import kotlin.jvm.JvmOverloads

/**
 * Returns the current value of this preference from the in-memory cache.
 *
 * Warning: During initialisation of [PreferenceManager2], pass [this] of [PreferenceManager2] instead
 * of leaving it blank or else we would be initialising it too early and cause a [StackOverflowError]
 */
@JvmOverloads
fun <C, S> PreferenceImpl<C, S>.firstCached(
    prefs2: PreferenceManager2 = PreferenceManager2.getInstance(LawnchairApp.instance),
): C {
    return getFromPreferences(prefs2.getCachedPreferences())
}
