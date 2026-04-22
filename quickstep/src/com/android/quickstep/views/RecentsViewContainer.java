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

package com.android.quickstep.views;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.LocusId;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;

import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.logger.LauncherAtom;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimView;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.util.SplitSelectStateController;

/**
 * Interface to be implemented by the parent view of RecentsView
 */
public interface RecentsViewContainer extends ActivityContext {

    /**
     * Returns an instance of an implementation of RecentsViewContainer
     * @param context will find instance of recentsViewContainer from given context.
     */
    static <T extends Context & RecentsViewContainer> T containerFromContext(Context context) {
        if (context instanceof RecentsViewContainer) {
            return (T) context;
        } else if (context instanceof ContextWrapper) {
            return containerFromContext(((ContextWrapper) context).getBaseContext());
        } else {
            throw new IllegalArgumentException("Cannot find RecentsViewContainer in parent tree");
        }
    }

    /**
     * Returns {@link ScrimView}
     */
    ScrimView getScrimView();

    /**
     * Returns the BaseContainerInterface to interact with RecentsViewContainer.
     */
    <T extends BaseContainerInterface<?, ?>> T getContainerInterface();

    /**
     * Returns the Overview Panel as a View
     */
    <T extends View> T getOverviewPanel();

    /**
     * @see Window.Callback#dispatchGenericMotionEvent(MotionEvent)
     */
    boolean dispatchGenericMotionEvent(MotionEvent ev);

    /**
     * @see Window.Callback#dispatchKeyEvent(KeyEvent)
     */
    boolean dispatchKeyEvent(KeyEvent ev);

    /**
     * Returns overview actions view as a view
     */
    OverviewActionsView getActionsView();

    /**
     * @see BaseActivity#addForceInvisibleFlag(int)
     * @param flag {@link BaseActivity.InvisibilityFlags}
     */
    void addForceInvisibleFlag(@BaseActivity.InvisibilityFlags int flag);

    /**
     * @see BaseActivity#clearForceInvisibleFlag(int)
     * @param flag {@link BaseActivity.InvisibilityFlags}
     */
    void clearForceInvisibleFlag(@BaseActivity.InvisibilityFlags int flag);

    /**
     * @see android.app.Activity#setLocusContext(LocusId, Bundle)
     * @param id {@link LocusId}
     * @param bundle {@link Bundle}
     */
    void setLocusContext(LocusId id, Bundle bundle);

    /**
     * @see BaseActivity#isStarted()
     * @return boolean
     */
    boolean isStarted();

    /**
     * @see BaseActivity#addEventCallback(int, Runnable)
     * @param event {@link BaseActivity.ActivityEvent}
     * @param callback runnable to be executed upon event
     */
    void addEventCallback(@BaseActivity.ActivityEvent int event, Runnable callback);

    /**
     * @see BaseActivity#removeEventCallback(int, Runnable)
     * @param event {@link BaseActivity.ActivityEvent}
     * @param callback runnable to be executed upon event
     */
    void removeEventCallback(@BaseActivity.ActivityEvent int event, Runnable callback);

    /**
     * @see com.android.quickstep.util.TISBindHelper#runOnBindToTouchInteractionService(Runnable)
     * @param r runnable to be executed upon event
     */
    void runOnBindToTouchInteractionService(Runnable r);

    /**
     * @see
     * BaseActivity#addMultiWindowModeChangedListener(BaseActivity.MultiWindowModeChangedListener)
     * @param listener {@link BaseActivity.MultiWindowModeChangedListener}
     */
    void addMultiWindowModeChangedListener(
            BaseActivity.MultiWindowModeChangedListener listener);

    /**
     * @see
     * BaseActivity#removeMultiWindowModeChangedListener(
     * BaseActivity.MultiWindowModeChangedListener)
     * @param listener {@link BaseActivity.MultiWindowModeChangedListener}
     */
    void removeMultiWindowModeChangedListener(
            BaseActivity.MultiWindowModeChangedListener listener);

    /**
     * Begins transition from overview back to homescreen
     */
    void returnToHomescreen();

    /**
     * True if the overview panel is visible.
     * @return Boolean
     */
    boolean isRecentsViewVisible();

    /**
     * Begins transition to start home through container
     */
    default void startHome(){
        // no op
    }

    /**
     * Checks container to see if we can start home transition safely
     */
    boolean canStartHomeSafely();


    /**
     * Enter staged split directly from the current running app.
     * @param leftOrTop if the staged split will be positioned left or top.
     */
    default void enterStageSplitFromRunningApp(boolean leftOrTop, int displayId) {}

    /**
     * Overwrites any logged item in Launcher that doesn't have a container with the
     * {@link com.android.launcher3.touch.PagedOrientationHandler} in use for Overview.
     *
     * @param itemInfoBuilder {@link LauncherAtom.ItemInfo.Builder}
     */
    default void applyOverwritesToLogItem(LauncherAtom.ItemInfo.Builder itemInfoBuilder) {
        if (!itemInfoBuilder.getContainerInfo().hasTaskSwitcherContainer()) {
            return;
        }

        if (!isRecentsViewVisible()) {
            return;
        }

        RecentsView<?, ?> recentsView = getOverviewPanel();
        var orientationForLogging =
                recentsView.getPagedOrientationHandler().getHandlerTypeForLogging();
        itemInfoBuilder.setContainerInfo(
                LauncherAtom.ContainerInfo.newBuilder()
                        .setTaskSwitcherContainer(
                                LauncherAtom.TaskSwitcherContainer.newBuilder()
                                        .setOrientationHandler(orientationForLogging))
                        .build());
    }

    void setTaskbarUIController(@Nullable TaskbarUIController taskbarUIController);

    @Nullable TaskbarUIController getTaskbarUIController();

    /**
     * Returns the Split Select State Controller
     */
    SplitSelectStateController getSplitSelectStateController();
}
