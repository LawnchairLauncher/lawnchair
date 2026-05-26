package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object Waterfox : QsbSearchProvider(
    id = "Waterfox",
    name = R.string.search_provider_waterfox,
    icon = R.drawable.ic_waterfox,
    themedIcon = R.drawable.ic_waterfox_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "net.waterfox.android.release",
    action = "org.mozilla.fenix.OPEN_TAB",
    className = "org.mozilla.fenix.IntentReceiverActivity",
    website = "https://github.com/BrowserWorks/Waterfox",
    type = QsbSearchProviderType.APP,
    supportVoiceIntent = false,
)
