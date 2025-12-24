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

package com.android.quickstep.dagger;

import com.android.app.displaylib.DisplayRepository;
import com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat;
import com.android.app.displaylib.PerDisplayRepository;
import com.android.launcher3.LifecycleTracker;
import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.model.WellbeingModel;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.taskbar.TaskbarModelCallbacksFactory;
import com.android.launcher3.taskbar.TaskbarUiStateMonitor;
import com.android.launcher3.taskbar.TaskbarViewCallbacksFactory;
import com.android.launcher3.taskbar.bubbles.BubbleActivityStarter;
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContextFactory;
import com.android.quickstep.FallbackWindowInterface;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.RotationTouchHelper;
import com.android.quickstep.SimpleOrientationTouchTransformer;
import com.android.quickstep.SystemDecorationChangeObserver;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.TaskOverlayFactory;
import com.android.quickstep.TopTaskTracker;
import com.android.quickstep.actioncorner.ActionCornerHandler;
import com.android.quickstep.input.QuickstepKeyGestureEventsManager;
import com.android.quickstep.inputconsumers.NavHandleLongPressHandler;
import com.android.quickstep.logging.SettingsChangeLogger;
import com.android.quickstep.util.AsyncClockEventDelegate;
import com.android.quickstep.util.ContextualSearchHapticManager;
import com.android.quickstep.util.ContextualSearchStateManager;
import com.android.quickstep.views.RecentsDismissUtils;
import com.android.quickstep.window.RecentsWindowManager;
import com.android.quickstep.window.RecentsWindowTracker;

import java.util.Set;

/**
 * Launcher Quickstep base component for Dagger injection.
 *
 * This class is not actually annotated as a Dagger component, since it is not used directly as one.
 * Doing so generates unnecessary code bloat.
 *
 * See {@link LauncherAppComponent} for the one actually used.
 */
public interface QuickstepBaseAppComponent extends LauncherBaseAppComponent {

    WellbeingModel getWellbeingModel();

    AsyncClockEventDelegate getAsyncClockEventDelegate();

    SystemUiProxy getSystemUiProxy();

    BubbleActivityStarter getBubbleActivityStarter();

    OverviewComponentObserver getOverviewComponentObserver();

    DesktopVisibilityController getDesktopVisibilityController();

    TopTaskTracker getTopTaskTracker();

    ContextualSearchHapticManager getContextualSearchHapticManager();

    ContextualSearchStateManager getContextualSearchStateManager();

    PerDisplayRepository<RecentsAnimationDeviceState> getRecentsAnimationDeviceStateRepository();

    PerDisplayRepository<TaskAnimationManager> getTaskAnimationManagerRepository();

    PerDisplayRepository<RotationTouchHelper> getRotationTouchHelperRepository();

    PerDisplayRepository<RecentsWindowManager> getRecentsWindowManagerRepository();

    PerDisplayRepository<RecentsWindowTracker> getRecentsWindowTrackerRepository();

    PerDisplayRepository<FallbackWindowInterface> getFallbackWindowInterfaceRepository();

    RecentsModel getRecentsModel();

    RecentsDismissUtils.Factory getRecentsDismissUtilsFactory();

    SettingsChangeLogger getSettingsChangeLogger();

    SimpleOrientationTouchTransformer getSimpleOrientationTouchTransformer();

    SystemDecorationChangeObserver getSystemDecorationChangeObserver();

    DisplayRepository getDisplayRepository();
    NavHandleLongPressHandler getNavHandleLongPressHandler();

    ActionCornerHandler.Factory getActionCornerHandlerFactory();

    OverviewCommandHelper.Factory getOverviewCommandHelperFactory();

    DisplaysWithDecorationsRepositoryCompat getDisplaysWithDecorationsRepositoryCompat();

    TaskbarModelCallbacksFactory getTaskbarModelCallbacksFactory();

    TaskbarViewCallbacksFactory getTaskbarViewCallbacksFactory();

    TaskbarOverlayContextFactory getTaskbarOverlayContextFactory();

    TaskOverlayFactory getTaskOverlayFactory();

    TaskbarUiStateMonitor getTaskbarUiStateMonitor();

    TaskbarFeatureEvaluator getTaskbarFeatureEvaluator();

    Set<LifecycleTracker> getLifecycleTrackers();

    QuickstepKeyGestureEventsManager getQuickstepKeyGestureEventsManager();
}
