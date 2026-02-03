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

package com.android.launcher3.widgetpicker

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.compose.ComposeFacade
import com.android.launcher3.compose.core.widgetpicker.WidgetPickerComposeWrapper
import com.android.launcher3.concurrent.annotations.BackgroundContext
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.util.ApiWrapper
import com.android.launcher3.widgetpicker.WidgetPickerConfig.Companion.EXTRA_IS_PENDING_WIDGET_DRAG
import com.android.launcher3.widgetpicker.data.repository.WidgetAppIconsRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetUsersRepository
import com.android.launcher3.widgetpicker.data.repository.WidgetsRepository
import com.android.launcher3.widgetpicker.listeners.WidgetPickerAddItemListener
import com.android.launcher3.widgetpicker.listeners.WidgetPickerDragItemListener
import com.android.launcher3.widgetpicker.shared.model.HostConstraint
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.shared.model.isAppWidget
import com.android.launcher3.widgetpicker.theme.darkWidgetPickerColors
import com.android.launcher3.widgetpicker.theme.lightWidgetPickerColors
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.WidgetPickerEventListeners
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.launch

/**
 * An helper that bootstraps widget picker UI (from [WidgetPickerComponent]) in to
 * [WidgetPickerActivity] when compose is available and widget picker refactor flags are on.
 *
 * Sets up the bindings necessary for widget picker component.
 */
class WidgetPickerComposeWrapperImpl
@Inject
constructor(
    private val widgetPickerComponentProvider: Provider<WidgetPickerComponent.Factory>,
    private val widgetsRepository: WidgetsRepository,
    private val widgetUsersRepository: WidgetUsersRepository,
    private val widgetAppIconsRepository: WidgetAppIconsRepository,
    @BackgroundContext private val backgroundContext: CoroutineContext,
    @ApplicationContext private val appContext: Context,
    private val apiWrapper: ApiWrapper,
) : WidgetPickerComposeWrapper {

    override fun showAllWidgets(
        activity: WidgetPickerActivity,
        widgetPickerConfig: WidgetPickerConfig,
    ) {
        val widgetPickerComponent = newWidgetPickerComponent(widgetPickerConfig)
        val callbacks = activity.buildEventListeners(widgetPickerConfig, apiWrapper)

        val fullWidgetsCatalog = widgetPickerComponent.getFullWidgetsCatalog()
        val composeView = ComposeFacade.initComposeView(activity.asContext()) as ComposeView

        composeView.apply {
            setContent {
                val scope = rememberCoroutineScope()
                val view = LocalView.current

                val widgetPickerColors =
                    if (isSystemInDarkTheme()) {
                        darkWidgetPickerColors()
                    } else {
                        lightWidgetPickerColors()
                    }

                MaterialTheme { // TODO(b/408283627): Use launcher theme.
                    WidgetPickerTheme(colors = widgetPickerColors) {
                        val eventListeners = remember { callbacks }
                        fullWidgetsCatalog.Content(eventListeners)
                    }
                }

                DisposableEffect(view) {
                    scope.launch { initializeRepositories() }

                    onDispose { cleanUpRepositories() }
                }
            }
        }

        checkNotNull(activity.dragLayer).addView(composeView)
    }

    private fun newWidgetPickerComponent(
        widgetPickerConfig: WidgetPickerConfig
    ): WidgetPickerComponent {
        return widgetPickerComponentProvider
            .get()
            .build(
                widgetsRepository = widgetsRepository,
                widgetUsersRepository = widgetUsersRepository,
                widgetAppIconsRepository = widgetAppIconsRepository,
                widgetHostInfo =
                    WidgetHostInfo(
                        title =
                            widgetPickerConfig.title
                                ?: appContext.resources.getString(R.string.widget_button_text),
                        description = widgetPickerConfig.description,
                        constraints = widgetPickerConfig.asHostConstraints(),
                        showDragShadow = !widgetPickerConfig.isForHomeScreen,
                    ),
                backgroundContext = backgroundContext,
            )
    }

    private fun initializeRepositories() {
        widgetsRepository.initialize()
        widgetUsersRepository.initialize()
        widgetAppIconsRepository.initialize()
    }

    private fun cleanUpRepositories() {
        widgetsRepository.cleanUp()
        widgetUsersRepository.cleanUp()
        widgetAppIconsRepository.cleanUp()
    }

    companion object {
        private const val HOME_SCREEN_WIDGET_INTERACTION_REASON_STRING =
            "WidgetPickerActivity.OnWidgetInteraction"

        private fun WidgetPickerActivity.buildEventListeners(
            widgetPickerConfig: WidgetPickerConfig,
            apiWrapper: ApiWrapper,
        ) =
            object : WidgetPickerEventListeners {
                override fun onClose() {
                    finish()
                }

                override fun onWidgetInteraction(widgetInteractionInfo: WidgetInteractionInfo) {
                    if (widgetPickerConfig.isForHomeScreen) {
                        handleWidgetInteractionForHomeScreen(widgetInteractionInfo, apiWrapper)
                    } else {
                        handleWidgetInteractionForExternalHost(widgetInteractionInfo)
                    }
                }
            }

        /**
         * Handles communication with the home screen about the "add" and "drag" interactions on
         * widgets within widget picker.
         *
         * For home screen, we register a listener that is called back when home screen is shown;
         * - WidgetPickerDragItemListener: bootstraps the drag helper that displays the shadow and
         *   handles the drag until completion.
         * - WidgetPickerAddItemListener: once launcher is shown, triggers the flow to add the
         *   widget to workspace.
         */
        private fun WidgetPickerActivity.handleWidgetInteractionForHomeScreen(
            interactionInfo: WidgetInteractionInfo,
            apiWrapper: ApiWrapper,
        ) {
            val interactionListener =
                when (interactionInfo) {
                    is WidgetInteractionInfo.WidgetDragInfo ->
                        WidgetPickerDragItemListener(
                            mimeType = interactionInfo.mimeType,
                            widgetInfo = interactionInfo.widgetInfo,
                            widgetPreview = interactionInfo.previewInfo,
                            previewRect = interactionInfo.bounds,
                            previewWidth = interactionInfo.widthPx,
                        )

                    is WidgetInteractionInfo.WidgetAddInfo ->
                        WidgetPickerAddItemListener(interactionInfo.widgetInfo)
                }
            Launcher.ACTIVITY_TRACKER.registerCallback(
                interactionListener,
                HOME_SCREEN_WIDGET_INTERACTION_REASON_STRING,
            )
            startActivity(
                /*intent=*/ Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(packageName)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                /*options=*/ apiWrapper.createFadeOutAnimOptions().toBundle(),
            )
            finish()
        }

        /**
         * Handles communication with the external host about the "add" and "drag" interactions on
         * widgets within widget picker.
         * - In case of drag and drop, finishes the activity with result indicating that there is a
         *   pending drag [EXTRA_IS_PENDING_WIDGET_DRAG] (that would contain the widget info as part
         *   of clip data) that the host should be handling.
         * - In case of add, finishes the activity with result containing extra information about
         *   the widget being added (namely [Intent.EXTRA_COMPONENT_NAME] and [Intent.EXTRA_USER].
         */
        private fun WidgetPickerActivity.handleWidgetInteractionForExternalHost(
            widgetInteractionInfo: WidgetInteractionInfo
        ) {
            when (widgetInteractionInfo) {
                is WidgetInteractionInfo.WidgetDragInfo ->
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_IS_PENDING_WIDGET_DRAG, true))

                is WidgetInteractionInfo.WidgetAddInfo -> {
                    val widgetInfo = widgetInteractionInfo.widgetInfo
                    if (widgetInfo.isAppWidget()) {
                        val providerInfo = widgetInfo.appWidgetProviderInfo
                        setResult(
                            RESULT_OK,
                            Intent().apply {
                                putExtra(Intent.EXTRA_COMPONENT_NAME, providerInfo.provider)
                                putExtra(Intent.EXTRA_USER, providerInfo.profile)
                            },
                        )
                    } else {
                        throw IllegalStateException(
                            "AppWidgetInfo not provided for external host drag"
                        )
                    }
                }
            }

            finish()
        }

        /** Builds the host constraints to provide to the widget picker module. */
        fun WidgetPickerConfig.asHostConstraints() = buildList {
            if (filteredUsers.isNotEmpty()) {
                add(HostConstraint.HostUserConstraint(filteredUsers))
            }
            if (!isForHomeScreen) {
                add(HostConstraint.NoShortcutsConstraint)
            }
            if (categoryInclusionFilter != 0 || categoryExclusionFilter != 0) {
                add(
                    HostConstraint.HostCategoryConstraint(
                        categoryInclusionMask = categoryInclusionFilter,
                        categoryExclusionMask = categoryExclusionFilter,
                    )
                )
            }
        }
    }
}
