package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object Mull : QsbSearchProvider(
    id = "Mull",
    name = R.string.search_provider_mull,
    icon = R.drawable.ic_mull,
    themedIcon = R.drawable.ic_mull_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "us.spotco.fennec_dos",
    action = "org.mozilla.fenix.OPEN_TAB",
    className = "org.mozilla.fenix.IntentReceiverActivity",
    website = "https://gitlab.com/divested-mobile/mull-fenix",
    type = QsbSearchProviderType.APP,
    supportVoiceIntent = true,
) {

    override fun handleCreateVoiceIntent(): Intent = Intent(action)
        .addFlags(INTENT_FLAGS)
        .setClassName(packageName, "org.chromium.chrome.browser.VoiceSearchActivity")
}
