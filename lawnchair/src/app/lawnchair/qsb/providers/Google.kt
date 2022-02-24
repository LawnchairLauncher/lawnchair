package app.lawnchair.qsb.providers

import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

object Google : QsbSearchProvider(
    id = "google",
    name = R.string.search_provider_google,
    icon = R.drawable.ic_super_g_color,
    themingMethod = ThemingMethod.THEME_BY_NAME,
    packageName = "com.google.android.googlequicksearchbox",
    action = "android.search.action.GLOBAL_SEARCH",
    supportVoiceIntent = true,
    website = "https://www.google.com/"
)
