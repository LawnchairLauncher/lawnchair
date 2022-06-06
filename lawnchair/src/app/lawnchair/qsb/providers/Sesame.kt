package app.lawnchair.qsb.providers

import app.lawnchair.qsb.ThemingMethod
import com.android.launcher3.R

object Sesame : QsbSearchProvider(
    id = "sesame",
    name = R.string.search_provider_sesame,
    icon = R.drawable.ic_sesame,
    themingMethod = ThemingMethod.TINT,
    packageName = "ninja.sesame.app.edge",
    className = "ninja.sesame.app.edge.omni.OmniActivity",
    website = "https://play.google.com/store/apps/details?id=ninja.sesame.app.edge",
    type = QsbSearchProviderType.APP
)