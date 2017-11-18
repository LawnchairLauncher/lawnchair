package ch.deletescape.lawnchair.preferences

import android.content.Context

object PreferenceProvider {

    // single instance for whole app
    private var preferenceIMPL: IPreferenceProvider? = null

    fun init(flags: IPreferenceProvider) {
        preferenceIMPL = flags
    }

    fun getPreferences(context: Context): IPreferenceProvider {
        if (preferenceIMPL == null)
            return PreferenceImpl(context)
        return preferenceIMPL as IPreferenceProvider
    }
}