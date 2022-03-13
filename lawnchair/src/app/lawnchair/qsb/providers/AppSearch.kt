package app.lawnchair.qsb.providers

import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

object AppSearch : QsbSearchProvider(
    id = "app_search",
    name = R.string.search_provider_app_search,
    icon = R.drawable.ic_qsb_search,
    themingMethod = ThemingMethod.TINT,
    packageName = "",
    website = ""
)