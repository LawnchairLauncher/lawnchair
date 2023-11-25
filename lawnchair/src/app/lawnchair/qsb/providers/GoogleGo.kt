package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object GoogleGo : QsbSearchProvider(
    id = "google_go",
    name = R.string.search_provider_google_go,
    icon = R.drawable.ic_super_g_color,
    themingMethod = ThemingMethod.THEME_BY_LAYER_ID,
    packageName = "com.google.android.apps.searchlite",
    action = "android.search.action.GLOBAL_SEARCH",
    supportVoiceIntent = true,
    website = "https://www.google.com/",
    type = QsbSearchProviderType.APP,
) {

    override fun handleCreateVoiceIntent(): Intent =
        createSearchIntent().putExtra("openMic", true)
}
