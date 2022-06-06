package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

object Presearch : QsbSearchProvider(
    id = "presearch",
    name = R.string.search_provider_presearch,
    icon = R.drawable.ic_presearch,
    themedIcon = R.drawable.ic_presearch_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "com.presearch",
    className = "org.chromium.chrome.browser.TextSearchActivity",
    supportVoiceIntent = true,
    website = "https://presearch.com/"
) {

    override fun handleCreateVoiceIntent(): Intent =
        Intent(action)
            .addFlags(INTENT_FLAGS)
            .setClassName(packageName, "org.chromium.chrome.browser.VoiceSearchActivity")

}