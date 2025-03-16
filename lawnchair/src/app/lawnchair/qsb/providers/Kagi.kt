package app.lawnchair.qsb.providers

import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

data object Kagi : QsbSearchProvider(
    id = "kagi",
    name = R.string.search_provider_kagi,
    icon = R.drawable.ic_kagi,
    themedIcon = R.drawable.ic_kagi_tinted,
    themingMethod = ThemingMethod.TINT,
    packageName = "",
    website = "https://kagi.com",
    type = QsbSearchProviderType.LOCAL,
    sponsored = false,
)
