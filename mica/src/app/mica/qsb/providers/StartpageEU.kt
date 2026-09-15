package app.mica.qsb.providers

import app.mica.animateToAllApps
import app.mica.preferences.PreferenceManager
import app.mica.qsb.ThemingMethod
import com.android.launcher3.Launcher
import com.android.launcher3.R

data object StartpageEU : QsbSearchProvider(
    id = "startpage-eu",
    name = R.string.search_provider_startpage_eu,
    icon = R.drawable.ic_startpage,
    themingMethod = ThemingMethod.TINT,
    packageName = "",
    website = "https://eu.startpage.com/?segment=startpage.mica",
    type = QsbSearchProviderType.LOCAL,
    sponsored = false,
) {
    override suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        val prefs = PreferenceManager.getInstance(launcher)
        val useWebSuggestions = prefs.searchResultStartPageSuggestion.get()

        if (useWebSuggestions) {
            launcher.animateToAllApps()
            launcher.appsView.searchUiManager.editText?.showKeyboard()
        } else {
            super.launch(launcher, forceWebsite)
        }
    }
}
