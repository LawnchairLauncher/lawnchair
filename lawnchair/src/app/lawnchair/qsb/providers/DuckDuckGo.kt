package app.lawnchair.qsb.providers

import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

object DuckDuckGo : QsbSearchProvider(
    id = "duckduckgo",
    name = R.string.search_provider_duckduckgo,
    icon = R.drawable.ic_duckduckgo,
    themedIcon = R.drawable.ic_duckduckgo_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "com.duckduckgo.mobile.android",
    action = "com.duckduckgo.mobile.android.NEW_SEARCH",
    website = "https://duckduckgo.com/"
)