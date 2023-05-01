package app.lawnchair.qsb.providers

import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

object PixelSearch : QsbSearchProvider(
    id = "pixel_search",
    name = R.string.search_provider_pixel_search,
    icon = R.drawable.ic_qsb_search,
    themingMethod = ThemingMethod.TINT,
    packageName = "rk.android.app.pixelsearch",
    website = "https://play.google.com/store/apps/details?id=rk.android.app.pixelsearch",
    type = QsbSearchProviderType.APP,
)