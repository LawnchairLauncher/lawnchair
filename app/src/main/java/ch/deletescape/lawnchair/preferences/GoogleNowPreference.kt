package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v14.preference.SwitchPreference
import android.util.AttributeSet
import ch.deletescape.lawnchair.R
import ch.deletescape.lawnchair.overlay.ILauncherClient

class GoogleNowPreference(context: Context, attrs: AttributeSet) : SwitchPreference(context, attrs) {

    private val enabledState = ILauncherClient.getEnabledState(context)

    init {
        isEnabled = enabledState == ILauncherClient.ENABLED
        if (!isEnabled) {
            if (enabledState == ILauncherClient.DISABLED_NO_PROXY_APP)
                setSummary(R.string.lawnfeed_not_found)
            if (enabledState == ILauncherClient.DISABLED_CLIENT_OUTDATED)
                setSummary(R.string.lawnfeed_incompatible)
            if (enabledState == ILauncherClient.DISABLED_NO_GOOGLE_APP)
                setSummary(R.string.google_app_not_found)
        }
    }
}