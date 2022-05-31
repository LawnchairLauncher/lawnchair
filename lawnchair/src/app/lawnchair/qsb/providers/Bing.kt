package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

object Bing : QsbSearchProvider(
    id = "bing",
    name = R.string.search_provider_bing,
    icon = R.drawable.ic_bing,
    themingMethod = ThemingMethod.TINT,
    packageName = "com.microsoft.bing",
    className = "com.microsoft.clients.bing.autosuggest.AutoSuggestActivity",
    supportVoiceIntent = true,
    website = "https://bing.com/"
) {

    override fun handleCreateVoiceIntent(): Intent =
        Intent(action)
            .addFlags(INTENT_FLAGS)
            .setClassName(packageName, "com.microsoft.clients.bing.voice.VoiceActivity")

}