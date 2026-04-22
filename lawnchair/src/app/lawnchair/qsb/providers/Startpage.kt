package app.lawnchair.qsb.providers

import app.lawnchair.animateToAllApps
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.Launcher
import com.android.launcher3.R

data object Startpage : QsbSearchProvider(
    id = "startpage",
    name = R.string.search_provider_startpage,
    icon = R.drawable.ic_startpage,
    themingMethod = ThemingMethod.TINT,
    packageName = "",
    website = "https://startpage.com/?segment=startpage.lawnchair",
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
