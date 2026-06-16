package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object FirefoxNightly : QsbSearchProvider(
    id = "FirefoxNightly",
    name = R.string.search_provider_firefox_nightly,
    icon = R.drawable.ic_firefox_nightly,
    themedIcon = R.drawable.ic_firefox_nightly_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "org.mozilla.fenix",
    action = "org.mozilla.fenix.OPEN_TAB",
    className = "org.mozilla.fenix.IntentReceiverActivity",
    website = "https://play.google.com/store/apps/details?id=org.mozilla.fenix",
    type = QsbSearchProviderType.APP,
    supportVoiceIntent = false,
) {

    override fun handleCreateVoiceIntent(): Intent = Intent(action)
        .addFlags(INTENT_FLAGS)
        .setClassName(packageName, "org.chromium.chrome.browser.VoiceSearchActivity")
}
