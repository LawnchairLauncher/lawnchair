package app.lawnchair.qsb.providers

import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object Vivaldi : QsbSearchProvider(
    id = "vivaldi",
    name = R.string.search_provider_vivaldi,
    icon = R.drawable.ic_vivaldi,
    themedIcon = R.drawable.ic_vivaldi_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "com.vivaldi.browser",
    className = "org.chromium.chrome.browser.searchwidget.SearchWidgetProviderActivity",
    website = "https://vivaldi.com/",
)
