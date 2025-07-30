package app.lawnchair.search.algorithms.engine

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.search.adapter.SPACE
import app.lawnchair.search.adapter.SearchTargetCompat
import app.lawnchair.search.adapter.SearchTargetFactory
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking

sealed interface SectionBuilder {
    /**
     * Takes the full list of results and returns a list of SearchTargetCompat
     * objects for its specific section, or an empty list if its section
     * should not be displayed.
     */
    fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat>
}

data object ContactsSectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val contacts = results.filterIsInstance<SearchResult.Contact>()
        if (contacts.isEmpty()) {
            return emptyList()
        }

        val targets = mutableListOf<SearchTargetCompat>()
        targets.add(factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_contacts_from_device)))
        targets.addAll(contacts.map { factory.createContactsTarget(it.data) })
        targets.add(factory.createHeaderTarget(SPACE))
        return targets
    }
}

data object FilesSectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val files = results.filterIsInstance<SearchResult.File>()
        if (files.isEmpty()) {
            return emptyList()
        }

        val targets = mutableListOf<SearchTargetCompat>()
        targets.add(factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_files)))
        targets.addAll(files.map { factory.createFilesTarget(it.data) })
        targets.add(factory.createHeaderTarget(SPACE))
        return targets
    }
}

data object SettingsSectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val settings = results.filterIsInstance<SearchResult.Setting>()

        if (settings.isEmpty()) {
            return emptyList()
        }

        val targets = mutableListOf<SearchTargetCompat>()
        targets.add(factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_settings_entry_from_device)))
        targets.addAll(settings.mapNotNull { factory.createSettingsTarget(it.data) })
        targets.add(factory.createHeaderTarget(SPACE))
        return targets
    }
}

data object CalculationSectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val calculations = results.filterIsInstance<SearchResult.Calculation>()
        if (calculations.isEmpty()) {
            return emptyList()
        }
        val targets = mutableListOf<SearchTargetCompat>()
        targets.add(factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_calculator)))
        targets.addAll(calculations.map { factory.createCalculatorTarget(it.data) })
        targets.add(factory.createHeaderTarget(SPACE))
        return targets
    }
}

data object WebSuggestionsSectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val webSuggestions = results.filterIsInstance<SearchResult.WebSuggestion>()
        if (webSuggestions.isEmpty()) {
            return emptyList()
        }

        val targets = mutableListOf<SearchTargetCompat>()
        val suggestionsHeader =
            factory.createHeaderTarget(context.getString(R.string.all_apps_search_result_suggestions))
        targets.add(suggestionsHeader)
        targets.addAll(
            webSuggestions.map {
                factory.createWebSuggestionsTarget(
                    it.suggestion,
                    it.provider,
                )
            },
        )
        targets.add(factory.createHeaderTarget(SPACE))
        return targets
    }
}

data object HistorySectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val webSuggestion = PreferenceManager2.getInstance(context).webSuggestionProvider.firstBlocking()

        val history = results.filterIsInstance<SearchResult.History>()
        if (history.isEmpty()) {
            return emptyList()
        }

        val targets = mutableListOf<SearchTargetCompat>()
        targets.add(factory.createHeaderTarget(context.getString(R.string.search_pref_result_history_title)))
        targets.addAll(
            history.map {
                factory.createSearchHistoryTarget(
                    it.data,
                    webSuggestion::getSearchUrl,
                )
            },
        )
        targets.add(factory.createHeaderTarget(SPACE))
        return targets
    }
}

data object ActionsSectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val marketSearch = results.filterIsInstance<SearchResult.Action.MarketSearch>()
        val webSearch = results.filterIsInstance<SearchResult.Action.WebSearch>()

        val targets = mutableListOf<SearchTargetCompat>()

        if (marketSearch.isNotEmpty()) {
            factory.createMarketSearchTarget(marketSearch.first().query)?.let {
                targets.add(it)
            }
        }
        if (webSearch.isNotEmpty()) {
            factory.createWebSearchActionTarget(
                query = webSearch.first().query,
                providerName = webSearch.first().providerName,
                searchUrl = webSearch.first().searchUrl,
                providerIconRes = webSearch.first().providerIconRes,
                tintIcon = webSearch.first().tintIcon,
            ).let {
                targets.add(it)
            }
        }
        targets.add(factory.createHeaderTarget(SPACE))
        return targets
    }
}

data object AppsAndShortcutsSectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val apps = results.filterIsInstance<SearchResult.App>()
        val shortcuts = results.filterIsInstance<SearchResult.Shortcut>()
        val appResultCount = apps.size

        val targets = mutableListOf<SearchTargetCompat>()

        if (appResultCount == 1 && shortcuts.isNotEmpty()) {
            val singleApp = apps.first()
            targets.add(factory.createAppSearchTarget(singleApp.data, asRow = true))
            targets.addAll(shortcuts.map { factory.createShortcutTarget(it.data) })
        } else {
            targets.addAll(apps.map { factory.createAppSearchTarget(it.data, asRow = false) })
        }
        targets.add(factory.createHeaderTarget(SPACE))

        return targets
    }
}

data object EmptyStateSectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val result = results.filterIsInstance<SearchResult.Action.EmptyState>()
        val targets = mutableListOf<SearchTargetCompat>()

        result.firstOrNull()?.let {
            targets.add(
                factory.createEmptyStateTarget(
                    it.titleRes,
                    it.subtitleRes,
                ),
            )
        }
        return targets
    }
}

data object SearchSettingsSectionBuilder : SectionBuilder {
    override fun build(
        context: Context,
        factory: SearchTargetFactory,
        results: List<SearchResult>,
    ): List<SearchTargetCompat> {
        val result = results.filterIsInstance<SearchResult.Action.SearchSettings>()
        val targets = mutableListOf<SearchTargetCompat>()

        result.firstOrNull()?.let {
            targets.add(
                factory.createSearchSettingsTarget(),
            )
        }
        return targets
    }
}
