/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.quickstep;

import static com.android.launcher3.LauncherState.OVERVIEW;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherInitListener;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.views.IconRecentsView;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * {@link ActivityControlHelper} for the in-launcher recents.
 * TODO: Implement the app to overview animation functionality
 */
public final class LauncherActivityControllerHelper extends GoActivityControlHelper<Launcher> {

    @Override
    public AnimationFactory prepareRecentsUI(Launcher activity,
            boolean activityVisible, boolean animateActivity,
            Consumer<AnimatorPlaybackController> callback) {
        LauncherState fromState = activity.getStateManager().getState();
        activity.<IconRecentsView>getOverviewPanel().setUsingRemoteAnimation(true);
        //TODO: Implement this based off where the recents view needs to be for app => recents anim.
        return new AnimationFactory() {
            @Override
            public void createActivityController(long transitionLength) {
                callback.accept(activity.getStateManager().createAnimationToNewWorkspace(
                        fromState, OVERVIEW, transitionLength));
            }

            @Override
            public void onTransitionCancelled() {}
        };
    }

    @Override
    public ActivityInitListener createActivityInitListener(
            BiPredicate<Launcher, Boolean> onInitListener) {
        return new LauncherInitListener(onInitListener);
    }

    @Override
    public Launcher getCreatedActivity() {
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app == null) {
            return null;
        }
        return (Launcher) app.getModel().getCallback();
    }

    private Launcher getVisibleLauncher() {
        Launcher launcher = getCreatedActivity();
        return (launcher != null) && launcher.isStarted() && launcher.hasWindowFocus() ?
                launcher : null;
    }

    @Override
    public IconRecentsView getVisibleRecentsView() {
        Launcher launcher = getVisibleLauncher();
        return launcher != null && launcher.getStateManager().getState().overviewUi
                ? launcher.getOverviewPanel() : null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Runnable onCompleteCallback) {
        Launcher launcher = getVisibleLauncher();
        if (launcher == null) {
            return false;
        }
        launcher.<IconRecentsView>getOverviewPanel().setUsingRemoteAnimation(false);
        launcher.getUserEventDispatcher().logActionCommand(
                LauncherLogProto.Action.Command.RECENTS_BUTTON,
                getContainerType(),
                LauncherLogProto.ContainerType.TASKSWITCHER);
        launcher.getStateManager().goToState(OVERVIEW,
                launcher.getStateManager().shouldAnimateStateChange(), onCompleteCallback);
        return true;
    }

    @Override
    public int getContainerType() {
        final Launcher launcher = getVisibleLauncher();
        return launcher != null ? launcher.getStateManager().getState().containerType
                : LauncherLogProto.ContainerType.APP;
    }

    @Override
    public void onLaunchTaskSuccess(Launcher launcher) {
        launcher.getStateManager().moveToRestState();
    }
}
