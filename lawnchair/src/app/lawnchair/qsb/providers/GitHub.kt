package app.lawnchair.qsb.providers

import com.android.launcher3.R

object GitHub : QsbSearchProvider(
    id = "github",
    name = R.string.search_provider_github,
    icon = R.drawable.ic_github,
    packageName = "com.github.android",
    className = "com.github.android.activities.SearchResultsActivity",
    website = "https://github.com/search"
)
