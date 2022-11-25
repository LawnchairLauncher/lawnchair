package app.lawnchair.qsb.providers

import com.android.launcher3.R

object GitHub : QsbSearchProvider(
    id = "github",
    name = R.string.search_provider_github,
    icon = R.drawable.ic_github,
    // todo: Add packageName & className back after https://github.com/orgs/community/discussions/39678 is resolved.
    packageName = "",
    website = "https://github.com/search",
    type = QsbSearchProviderType.WEBSITE,
)
