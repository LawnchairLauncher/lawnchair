/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.util

import android.app.contextualsearch.ContextualSearchManager
import android.app.contextualsearch.ContextualSearchManager.ENTRYPOINT_LONG_PRESS_HOME
import android.app.contextualsearch.ContextualSearchManager.FEATURE_CONTEXTUAL_SEARCH
import android.content.Context
import android.util.Log
import android.view.Display.DEFAULT_DISPLAY
import androidx.annotation.VisibleForTesting
import com.android.internal.app.AssistUtils
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_ASSISTANT_FAILED_SERVICE_ERROR
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_KEYGUARD
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_NOTIFICATION_SHADE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_ATTEMPTED_SPLITSCREEN
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_FAILED_NOT_AVAILABLE
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_FAILED_SETTING_DISABLED
import com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_LAUNCH_OMNI_SUCCESSFUL_HOME
import com.android.quickstep.BaseContainerInterface
import com.android.quickstep.DeviceConfigWrapper
import com.android.quickstep.OverviewComponentObserver
import com.android.quickstep.SystemUiProxy
import com.android.quickstep.TopTaskTracker
import com.android.quickstep.views.RecentsView
import com.android.systemui.shared.system.QuickStepContract
import javax.inject.Inject

/** Handles invocations and checks for Contextual Search. */
class ContextualSearchInvoker
internal constructor(
    private val context: Context,
    private val contextualSearchStateManager: ContextualSearchStateManager,
    private val topTaskTracker: TopTaskTracker,
    private val systemUiProxy: SystemUiProxy,
    private val statsLogManager: StatsLogManager,
    private val contextualSearchHapticManager: ContextualSearchHapticManager,
    private val contextualSearchManager: ContextualSearchManager?,
) {
    constructor(
        context: Context
    ) : this(
        context,
        ContextualSearchStateManager.INSTANCE[context],
        TopTaskTracker.INSTANCE[context],
        SystemUiProxy.INSTANCE[context],
        StatsLogManager.newInstance(context),
        ContextualSearchHapticManager.INSTANCE[context],
        context.getSystemService(ContextualSearchManager::class.java),
    )

    @Inject
    constructor(
        @ApplicationContext context: Context,
        contextualSearchStateManager: ContextualSearchStateManager,
        topTaskTracker: TopTaskTracker,
        systemUiProxy: SystemUiProxy,
        logManagerFactory: StatsLogManager.StatsLogManagerFactory,
        hapticManager: ContextualSearchHapticManager,
    ) : this(
        context,
        contextualSearchStateManager,
        topTaskTracker,
        systemUiProxy,
        logManagerFactory.create(context),
        hapticManager,
        context.getSystemService(ContextualSearchManager::class.java),
    )

    /** @return Array of AssistUtils.INVOCATION_TYPE_* that we want to handle instead of SysUI. */
    fun getSysUiAssistOverrideInvocationTypes(): IntArray {
        val overrideInvocationTypes = com.android.launcher3.util.IntArray()
        if (context.packageManager.hasSystemFeature(FEATURE_CONTEXTUAL_SEARCH)) {
            overrideInvocationTypes.add(AssistUtils.INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS)
        }
        return overrideInvocationTypes.toArray()
    }

    /**
     * @return `true` if the override was handled, i.e. an assist surface was shown or the request
     *   should be ignored. `false` means the caller should start assist another way.
     */
    fun tryStartAssistOverride(invocationType: Int): Boolean {
        if (invocationType == AssistUtils.INVOCATION_TYPE_HOME_BUTTON_LONG_PRESS) {
            if (!context.packageManager.hasSystemFeature(FEATURE_CONTEXTUAL_SEARCH)) {
                // When Contextual Search is disabled, fall back to Assistant.
                return false
            }

            val success = show(ENTRYPOINT_LONG_PRESS_HOME)
            if (success) {
                val runningPackage =
                    TopTaskTracker.INSTANCE[context].getCachedTopTask(
                            /* filterOnlyVisibleRecents */ true,
                            DEFAULT_DISPLAY,
                        )
                        .getPackageName()
                statsLogManager
                    .logger()
                    .withPackageName(runningPackage)
                    .log(LAUNCHER_LAUNCH_OMNI_SUCCESSFUL_HOME)
            }

            // Regardless of success, do not fall back to other assistant.
            return true
        }
        return false
    }

    /**
     * Invoke Contextual Search via ContextualSearchService if availability checks are successful
     *
     * @param entryPoint one of the ENTRY_POINT_* constants defined in this class
     * @return true if invocation was successful, false otherwise
     */
    fun show(entryPoint: Int): Boolean {
        return if (!runContextualSearchInvocationChecksAndLogFailures()) false
        else invokeContextualSearchUnchecked(entryPoint)
    }

    /**
     * Run availability checks and log errors to WW. If successful the caller is expected to call
     * {@link invokeContextualSearchUnchecked}
     *
     * @return true if availability checks were successful, false otherwise.
     */
    fun runContextualSearchInvocationChecksAndLogFailures(): Boolean {
        if (
            contextualSearchManager == null ||
                !context.packageManager.hasSystemFeature(FEATURE_CONTEXTUAL_SEARCH)
        ) {
            Log.i(TAG, "Contextual Search invocation failed: no ContextualSearchManager")
            statsLogManager.logger().log(LAUNCHER_LAUNCH_ASSISTANT_FAILED_SERVICE_ERROR)
            return false
        }
        if (!contextualSearchStateManager.isContextualSearchSettingEnabled) {
            Log.i(TAG, "Contextual Search invocation failed: setting disabled")
            statsLogManager.logger().log(LAUNCHER_LAUNCH_OMNI_FAILED_SETTING_DISABLED)
            return false
        }
        if (isNotificationShadeShowing()) {
            Log.i(TAG, "Contextual Search invocation failed: notification shade")
            statsLogManager.logger().log(LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_NOTIFICATION_SHADE)
            return false
        }
        if (isKeyguardShowing()) {
            Log.i(TAG, "Contextual Search invocation attempted: keyguard")
            statsLogManager.logger().log(LAUNCHER_LAUNCH_OMNI_ATTEMPTED_OVER_KEYGUARD)
            if (!contextualSearchStateManager.isInvocationAllowedOnKeyguard) {
                Log.i(TAG, "Contextual Search invocation failed: keyguard not allowed")
                return false
            } else if (!contextualSearchStateManager.supportsShowWhenLocked()) {
                Log.i(TAG, "Contextual Search invocation failed: AGA doesn't support keyguard")
                return false
            }
        }
        if (isInSplitscreen()) {
            Log.i(TAG, "Contextual Search invocation attempted: splitscreen")
            statsLogManager.logger().log(LAUNCHER_LAUNCH_OMNI_ATTEMPTED_SPLITSCREEN)
            if (!contextualSearchStateManager.isInvocationAllowedInSplitscreen) {
                Log.i(TAG, "Contextual Search invocation failed: splitscreen not allowed")
                return false
            }
        }
        if (!contextualSearchStateManager.isContextualSearchIntentAvailable) {
            Log.i(TAG, "Contextual Search invocation failed: no matching CSS intent filter")
            statsLogManager.logger().log(LAUNCHER_LAUNCH_OMNI_FAILED_NOT_AVAILABLE)
            return false
        }

        return true
    }

    /**
     * Invoke Contextual Search via ContextualSearchService and do haptic
     *
     * @param entryPoint Entry point identifier, passed to ContextualSearchService.
     * @return true if invocation was successful, false otherwise
     */
    fun invokeContextualSearchUncheckedWithHaptic(entryPoint: Int): Boolean {
        return invokeContextualSearchUnchecked(entryPoint, withHaptic = true)
    }

    private fun invokeContextualSearchUnchecked(
        entryPoint: Int,
        withHaptic: Boolean = false,
    ): Boolean {
        if (withHaptic && DeviceConfigWrapper.get().enableSearchHapticCommit) {
            contextualSearchHapticManager.vibrateForSearch()
        }
        if (contextualSearchManager == null) {
            return false
        }
        val recentsContainerInterface = getRecentsContainerInterface()
        if (recentsContainerInterface?.isInLiveTileMode() == true) {
            Log.i(TAG, "Contextual Search invocation attempted: live tile")
            endLiveTileMode(recentsContainerInterface) {
                contextualSearchManager.startContextualSearch(entryPoint)
            }
        } else {
            contextualSearchManager.startContextualSearch(entryPoint)
        }
        return true
    }

    private fun isInSplitscreen(): Boolean {
        return topTaskTracker.getRunningSplitTaskIds().isNotEmpty()
    }

    private fun isNotificationShadeShowing(): Boolean {
        return systemUiProxy.lastSystemUiStateFlags and SHADE_EXPANDED_SYSUI_FLAGS != 0L
    }

    private fun isKeyguardShowing(): Boolean {
        return systemUiProxy.lastSystemUiStateFlags and KEYGUARD_SHOWING_SYSUI_FLAGS != 0L
    }

    @VisibleForTesting
    fun getRecentsContainerInterface(): BaseContainerInterface<*, *>? {
        return OverviewComponentObserver.INSTANCE.get(context)
            .getContainerInterface(DEFAULT_DISPLAY)
    }

    /**
     * End the live tile mode.
     *
     * @param onCompleteRunnable Runnable to run when the live tile is paused. May run immediately.
     */
    private fun endLiveTileMode(
        recentsContainerInterface: BaseContainerInterface<*, *>?,
        onCompleteRunnable: Runnable,
    ) {
        val recentsViewContainer = recentsContainerInterface?.createdContainer
        if (recentsViewContainer == null) {
            onCompleteRunnable.run()
            return
        }
        val recentsView: RecentsView<*, *> = recentsViewContainer.getOverviewPanel()
        recentsView.switchToScreenshot {
            recentsView.finishRecentsAnimation(
                true, /* toRecents */
                false, /* shouldPip */
                onCompleteRunnable,
            )
        }
    }

    companion object {
        private const val TAG = "ContextualSearchInvoker"
        const val SHADE_EXPANDED_SYSUI_FLAGS =
            QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED or
                QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED
        const val KEYGUARD_SHOWING_SYSUI_FLAGS =
            (QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING or
                QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING or
                QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED)
    }
}
