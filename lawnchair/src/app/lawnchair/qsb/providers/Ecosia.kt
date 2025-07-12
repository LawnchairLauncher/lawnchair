package app.lawnchair.qsb.providers

import com.android.launcher3.R

data object Ecosia : QsbSearchProvider(
    id = "ecosia",
    name = R.string.search_provider_ecosia,
    icon = R.drawable.ic_ecosia,
    packageName = "com.ecosia.android",
    website = "https://www.ecosia.org/",
    type = QsbSearchProviderType.APP_AND_WEBSITE,
)
