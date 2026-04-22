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

package com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.shared.model.WidgetAppId
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfile
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.components.AppHeaderDescriptionStyle
import com.android.launcher3.widgetpicker.ui.components.LeadingIconToolbarTab
import com.android.launcher3.widgetpicker.ui.components.ScrollableFloatingToolbar
import com.android.launcher3.widgetpicker.ui.components.SinglePaneLayout
import com.android.launcher3.widgetpicker.ui.components.WidgetAppHeaderStyle
import com.android.launcher3.widgetpicker.ui.components.WidgetAppsList
import com.android.launcher3.widgetpicker.ui.components.widgetPickerTestTag
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.DEFAULT_SELECTED_TAB
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.FEATURED_TAB_INDEX
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.PERSONAL_TAB_INDEX
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.TABS_COUNT_WITHOUT_WORK_PROFILE
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.TABS_COUNT_WITH_WORK_PROFILE
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.WORK_TAB_INDEX
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.bottomTabsBottomPadding
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.bottomTabsHorizontalPadding
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.bottomTabsTopPadding
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.contentBottomEdgeSpacing
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.contentShape
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneDimens.pagerItemsSpacing
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneTestTags.FEATURED_WIDGETS_TAB_TEST_TAG
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneTestTags.PERSONAL_WIDGETS_TAB_TEST_TAG
import com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing.LandingScreenSinglePaneTestTags.WORK_WIDGETS_TAB_TEST_TAG
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlinx.coroutines.launch

/**
 * A composable function that provides a single pane layout for landing screen of the full catalog
 * of widgets in the widget picker.
 */
@Composable
fun LandingScreenSinglePane(
    searchBarContent: @Composable () -> Unit,
    featuredWidgetsContent: @Composable () -> Unit,
    widgetAppIconsState: AppIconsState,
    browseWidgetsState: BrowseWidgetsState.Data,
    personalWidgetPreviewsState: PreviewsState,
    workWidgetPreviewsState: PreviewsState,
    selectedPersonalWidgetAppId: WidgetAppId?,
    onPersonalWidgetAppToggle: (WidgetAppId) -> Unit,
    selectedWorkWidgetAppId: WidgetAppId?,
    onWorkWidgetAppToggle: (WidgetAppId) -> Unit,
    onWidgetInteraction: (WidgetInteractionInfo) -> Unit,
    showDragShadow: Boolean,
) {
    val hasWorkProfile = remember(browseWidgetsState) { browseWidgetsState.workProfile != null }

    val pagerState =
        rememberPagerState(
            initialPage = DEFAULT_SELECTED_TAB,
            pageCount = {
                if (hasWorkProfile) {
                    TABS_COUNT_WITH_WORK_PROFILE
                } else {
                    TABS_COUNT_WITHOUT_WORK_PROFILE
                }
            },
        )

    SinglePaneLayout(
        searchBar = searchBarContent,
        bottomFloatingContent = {
            BottomTabs(
                personalUserProfile = browseWidgetsState.personalProfile,
                workUserProfile = browseWidgetsState.workProfile,
                pagerState = pagerState,
            )
        },
        content = {
            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                pageSpacing = pagerItemsSpacing,
                modifier = Modifier.fillMaxWidth().clip(contentShape),
            ) { pageIndex ->
                when (pageIndex) {
                    FEATURED_TAB_INDEX -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier.fillMaxSize()
                                    .clip(contentShape)
                                    .background(WidgetPickerTheme.colors.widgetsContainerBackground)
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = contentBottomEdgeSpacing),
                        ) {
                            featuredWidgetsContent()
                        }
                    }

                    PERSONAL_TAB_INDEX -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            WidgetAppsList(
                                modifier = Modifier.fillMaxSize(),
                                widgetApps = browseWidgetsState.personalWidgetApps,
                                selectedWidgetAppId = selectedPersonalWidgetAppId,
                                widgetAppHeaderStyle = WidgetAppHeaderStyle.EXPANDABLE,
                                headerDescriptionStyle = AppHeaderDescriptionStyle.WIDGETS_COUNT,
                                onWidgetAppClick = { widgetApp ->
                                    onPersonalWidgetAppToggle(widgetApp.id)
                                },
                                appIcons = widgetAppIconsState.icons,
                                widgetPreviews = personalWidgetPreviewsState.previews,
                                onWidgetInteraction = onWidgetInteraction,
                                showDragShadow = showDragShadow,
                                bottomContentSpacing = contentBottomEdgeSpacing,
                            )
                        }
                    }

                    WORK_TAB_INDEX ->
                        if (hasWorkProfile) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                WidgetAppsList(
                                    modifier = Modifier.fillMaxSize(),
                                    widgetApps = browseWidgetsState.workWidgetApps,
                                    selectedWidgetAppId = selectedWorkWidgetAppId,
                                    widgetAppHeaderStyle = WidgetAppHeaderStyle.EXPANDABLE,
                                    headerDescriptionStyle =
                                        AppHeaderDescriptionStyle.WIDGETS_COUNT,
                                    onWidgetAppClick = { widgetApp ->
                                        onWorkWidgetAppToggle(widgetApp.id)
                                    },
                                    appIcons = widgetAppIconsState.icons,
                                    widgetPreviews = workWidgetPreviewsState.previews,
                                    onWidgetInteraction = onWidgetInteraction,
                                    showDragShadow = showDragShadow,
                                    bottomContentSpacing = contentBottomEdgeSpacing,
                                    emptyWidgetsErrorMessage =
                                        browseWidgetsState.workProfile?.let { workProfile ->
                                            workProfile.pausedProfileMessage.takeIf {
                                                workProfile.paused
                                            }
                                        },
                                )
                            }
                        }
                }
            }
        },
    )
}

@Composable
private fun BottomTabs(
    pagerState: PagerState,
    personalUserProfile: WidgetUserProfile,
    workUserProfile: WidgetUserProfile?,
) {
    val scope = rememberCoroutineScope()

    val tabs: List<@Composable () -> Unit> = buildList {
        add {
            LeadingIconToolbarTab(
                label = stringResource(R.string.featured_widgets_tab_label),
                leadingIcon = Icons.Filled.Star,
                selected = pagerState.currentPage == FEATURED_TAB_INDEX,
                onClick = { scope.launch { pagerState.animateScrollToPage(FEATURED_TAB_INDEX) } },
                modifier = Modifier.widgetPickerTestTag(FEATURED_WIDGETS_TAB_TEST_TAG),
            )
        }

        if (workUserProfile == null) {
            add {
                LeadingIconToolbarTab(
                    label = stringResource(R.string.browse_widgets_tab_label),
                    leadingIcon = Icons.AutoMirrored.Filled.List,
                    selected = pagerState.currentPage == PERSONAL_TAB_INDEX,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(PERSONAL_TAB_INDEX) }
                    },
                    modifier = Modifier.widgetPickerTestTag(PERSONAL_WIDGETS_TAB_TEST_TAG),
                )
            }
        } else {
            add {
                LeadingIconToolbarTab(
                    label = personalUserProfile.label,
                    leadingIcon = Icons.Filled.Person,
                    selected = pagerState.currentPage == PERSONAL_TAB_INDEX,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(PERSONAL_TAB_INDEX) }
                    },
                    modifier = Modifier.widgetPickerTestTag(PERSONAL_WIDGETS_TAB_TEST_TAG),
                )
            }
            add {
                LeadingIconToolbarTab(
                    label = workUserProfile.label,
                    leadingIcon = Icons.Outlined.Work,
                    selected = pagerState.currentPage == WORK_TAB_INDEX,
                    onClick = { scope.launch { pagerState.animateScrollToPage(WORK_TAB_INDEX) } },
                    modifier = Modifier.widgetPickerTestTag(WORK_WIDGETS_TAB_TEST_TAG),
                )
            }
        }
    }

    ScrollableFloatingToolbar(
        modifier =
            Modifier.padding(horizontal = bottomTabsHorizontalPadding)
                .padding(top = bottomTabsTopPadding, bottom = bottomTabsBottomPadding),
        selectedTabIndex = pagerState.currentPage,
        tabs = tabs,
    )
}

private object LandingScreenSinglePaneDimens {
    const val TABS_COUNT_WITH_WORK_PROFILE = 3
    const val TABS_COUNT_WITHOUT_WORK_PROFILE = 2

    const val FEATURED_TAB_INDEX = 0
    const val PERSONAL_TAB_INDEX = 1
    const val WORK_TAB_INDEX = 2
    const val DEFAULT_SELECTED_TAB = FEATURED_TAB_INDEX

    val contentShape = RoundedCornerShape(24.dp)
    val pagerItemsSpacing = 8.dp

    val bottomTabsTopPadding = 8.dp
    val bottomTabsHorizontalPadding = 32.dp
    val bottomTabsBottomPadding = 8.dp

    // Single pane always shows floating tabs over the content; hence has a static bottom spacing.
    val contentBottomEdgeSpacing = 70.dp
}

private object LandingScreenSinglePaneTestTags {
    const val FEATURED_WIDGETS_TAB_TEST_TAG = "featured_widgets_tab"
    const val PERSONAL_WIDGETS_TAB_TEST_TAG = "personal_widgets_tab"
    const val WORK_WIDGETS_TAB_TEST_TAG = "work_widgets_tab"
}
