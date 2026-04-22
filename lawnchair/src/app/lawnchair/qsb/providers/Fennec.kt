package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object Fennec : QsbSearchProvider(
    id = "Fennec",
    name = R.string.search_provider_fennec,
    icon = R.drawable.ic_fennec,
    themedIcon = R.drawable.ic_fennec_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "org.mozilla.fennec_fdroid",
    action = "org.mozilla.fenix.OPEN_TAB",
    className = "org.mozilla.fenix.IntentReceiverActivity",
    website = "https://f-droid.org/packages/org.mozilla.fennec_fdroid/",
    type = QsbSearchProviderType.APP,
    supportVoiceIntent = true,
) {

    override fun handleCreateVoiceIntent(): Intent = Intent(action)
        .addFlags(INTENT_FLAGS)
        .setClassName(packageName, "org.chromium.chrome.browser.VoiceSearchActivity")
}
