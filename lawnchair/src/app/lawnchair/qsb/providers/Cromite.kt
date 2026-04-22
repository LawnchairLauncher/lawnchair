package app.lawnchair.qsb.providers

import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object Cromite : QsbSearchProvider(
    id = "cromite",
    name = R.string.search_provider_cromite,
    icon = R.drawable.ic_cromite,
    themedIcon = R.drawable.ic_cromite_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "org.cromite.cromite",
    action = "android.intent.action.WEB_SEARCH",
    className = "org.chromium.chrome.browser.searchwidget.SearchActivity",
    website = "https://github.com/uazo/cromite/releases/latest",
    type = QsbSearchProviderType.APP,
    supportVoiceIntent = false,
)
