package app.lawnchair.qsb.providers

import com.android.launcher3.R

/** Perplexity AI search — website-only hotseat QSB provider. */
data object Perplexity : QsbSearchProvider(
    id = "perplexity",
    name = R.string.search_provider_perplexity,
    icon = R.drawable.ic_qsb_search,
    packageName = "",
    website = "https://www.perplexity.ai/search?q=",
    type = QsbSearchProviderType.WEBSITE,
)
