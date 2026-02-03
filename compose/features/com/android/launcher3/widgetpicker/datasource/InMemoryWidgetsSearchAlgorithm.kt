/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.widgetpicker.datasource

import com.android.launcher3.concurrent.annotations.BackgroundContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.widgetpicker.shared.model.WidgetApp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * A simple in-memory implementation of [WidgetsSearchAlgorithm] that matches input query against
 * the app labels, widget title and description.
 *
 * More weight is given to app names than widget title; and description has lowest weight in scoring
 * the results.
 */
@LauncherAppSingleton
class InMemoryWidgetSearchAlgorithm @Inject constructor(
    @BackgroundContext
    private val backgroundContext: CoroutineContext,
) : WidgetsSearchAlgorithm {
    override suspend fun initialize() {}

    override suspend fun searchWidgets(
        query: String,
        corpus: List<WidgetApp>
    ): List<WidgetApp> = coroutineScope {
        // Ideally, uses search only one word, but supporting multiple words.
        val queryWords = query.trim().lowercase().split("\\s+".toRegex())
            .filter { it.isNotBlank() }

        if (queryWords.isEmpty()) {
            return@coroutineScope emptyList()
        }

        val results = corpus.map { widgetApp ->
            async(backgroundContext) {
                matchAndScoreWidgetApp(widgetApp, queryWords)
            }
        }

        results.awaitAll()
            .filter { it.first.widgets.isNotEmpty() && it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first } // Return just the WidgetApp
    }

    private fun matchAndScoreWidgetApp(
        widgetApp: WidgetApp,
        queryWords: List<String>
    ): Pair<WidgetApp, Int> {
        val appTitleScore = widgetApp.title?.let {
            matchAndCalculateScore(
                queryWords = queryWords,
                targetText = widgetApp.title.toString(),
                matchType = MatchType.APP_TITLE
            )
        } ?: 0

        val scoredWidgets = widgetApp.widgets.map { widget ->
            val labelScore =
                matchAndCalculateScore(
                    queryWords = queryWords,
                    targetText = widget.label,
                    matchType = MatchType.WIDGET_LABEL
                )
            val descriptionScore = widget.description?.let {
                matchAndCalculateScore(
                    queryWords = queryWords,
                    targetText = it.toString(),
                    matchType = MatchType.WIDGET_DESCRIPTION
                )
            } ?: 0

            val totalWidgetScore = labelScore + descriptionScore
            widget to totalWidgetScore
        }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

        val totalAppScore = appTitleScore + scoredWidgets.sumOf { it.second }

        return if (appTitleScore > 0) {
            widgetApp to appTitleScore + totalAppScore
        } else {
            widgetApp.copy(
                widgets = scoredWidgets.map { it.first }
            ) to totalAppScore
        }
    }

    private fun matchAndCalculateScore(
        queryWords: List<String>,
        targetText: String,
        matchType: MatchType
    ): Int {
        var totalScore = 0
        val wordsInTarget = targetText
            .lowercase()
            .split("\\s+".toRegex()).filter { it.isNotBlank() }

        for (queryWord in queryWords) {
            var wordScore = 0

            for (targetWord in wordsInTarget) {
                wordScore = wordScore.coerceAtLeast(
                    calculateSingleWordScore(
                        queryWord,
                        targetWord,
                        matchType
                    )
                )
            }
            totalScore += wordScore
        }
        return totalScore
    }

    private fun calculateSingleWordScore(
        queryWord: String,
        targetWord: String,
        matchType: MatchType
    ): Int {
        val baseScore = when {
            // exact matches are score higher.
            targetWord == queryWord -> 2
            // Then the matches that begin with given input as prefix
            targetWord.startsWith(queryWord) -> 1
            else -> 0
        }

        return baseScore * matchType.weightFactor
    }

    private enum class MatchType(val weightFactor: Int) {
        // Highest weight to app title matches; users are likely to search for apps
        APP_TITLE(12),

        // Medium weight to widget labels (lower than app title as some widgets might have app title
        // in name and based on number of items, might add unnecessary weight).
        WIDGET_LABEL(3),

        // Lowest weight to description; description might might not be as impactful as the labels
        // or app title; but, it might still help in cases where alternate words for the widgets
        // functionality are used (e.g. stocks vs watchlist).
        WIDGET_DESCRIPTION(1)
    }

    override fun cleanup() {}
}
