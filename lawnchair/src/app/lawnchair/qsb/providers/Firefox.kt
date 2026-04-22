package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object Firefox : QsbSearchProvider(
    id = "Firefox",
    name = R.string.search_provider_firefox,
    icon = R.drawable.ic_firefox,
    themedIcon = R.drawable.ic_firefox_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "org.mozilla.firefox",
    action = "org.mozilla.fenix.OPEN_TAB",
    className = "org.mozilla.fenix.IntentReceiverActivity",
    website = "https://play.google.com/store/apps/details?id=org.mozilla.firefox",
    type = QsbSearchProviderType.APP,
    supportVoiceIntent = false,
) {

    override fun handleCreateVoiceIntent(): Intent = Intent(action)
        .addFlags(INTENT_FLAGS)
        .setClassName(packageName, "org.chromium.chrome.browser.VoiceSearchActivity")
}
