package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object IronFox : QsbSearchProvider(
    id = "IronFox",
    name = R.string.search_provider_ironfox,
    icon = R.drawable.ic_ironfox,
    themedIcon = R.drawable.ic_ironfox_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "org.ironfoxoss.ironfox",
    action = "org.mozilla.fenix.OPEN_TAB",
    className = "org.mozilla.fenix.IntentReceiverActivity",
    website = "https://gitlab.com/ironfox-oss/IronFox",
    type = QsbSearchProviderType.APP,
    supportVoiceIntent = true,
) {

    override fun handleCreateVoiceIntent(): Intent = Intent(action)
        .addFlags(INTENT_FLAGS)
        .setClassName(packageName, "org.chromium.chrome.browser.VoiceSearchActivity")
}
