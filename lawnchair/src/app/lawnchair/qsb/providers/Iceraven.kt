package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object Iceraven : QsbSearchProvider(
    id = "Iceraven",
    name = R.string.search_provider_iceraven,
    icon = R.drawable.ic_iceraven,
    themedIcon = R.drawable.ic_iceraven_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "io.github.forkmaintainers.iceraven",
    action = "org.mozilla.fenix.OPEN_TAB",
    className = "org.mozilla.fenix.IntentReceiverActivity",
    website = "https://github.com/fork-maintainers/iceraven-browser/releases/latest",
    type = QsbSearchProviderType.APP,
    supportVoiceIntent = true,
) {

    override fun handleCreateVoiceIntent(): Intent = Intent(action)
        .addFlags(INTENT_FLAGS)
        .setClassName(packageName, "org.chromium.chrome.browser.VoiceSearchActivity")
}
