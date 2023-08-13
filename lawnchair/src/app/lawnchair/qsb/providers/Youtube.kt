package app.lawnchair.qsb.providers

import android.content.Intent
import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

object Youtube : QsbSearchProvider(
    id = "youtube",
    name = R.string.search_provider_youtube,
    icon = R.drawable.ic_youtube,
    themingMethod = ThemingMethod.THEME_BY_LAYER_ID,
    packageName = "com.google.android.youtube",
    action = Intent.ACTION_SEARCH,
    supportVoiceIntent = false,
    website = "https://youtube.com/"
)
