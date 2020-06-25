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

import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;
import static com.android.quickstep.fallback.RecentsState.BACKGROUND_APP;
import static com.android.quickstep.fallback.RecentsState.DEFAULT;

import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link BaseActivityInterface} for recents when the default launcher is different than the
 * currently running one and apps should interact with the {@link RecentsActivity} as opposed
 * to the in-launcher one.
 */
public final class FallbackActivityInterface extends
        BaseActivityInterface<RecentsState, RecentsActivity> {

    public static final FallbackActivityInterface INSTANCE = new FallbackActivityInterface();

    private FallbackActivityInterface() {
        super(false, DEFAULT, BACKGROUND_APP);
    }

    /** 2 */
    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect,
            PagedOrientationHandler orientationHandler) {
        calculateTaskSize(context, dp, outRect, orientationHandler);
        if (dp.isVerticalBarLayout()
                && SysUINavigationMode.INSTANCE.get(context).getMode() != NO_BUTTON) {
            Rect targetInsets = dp.getInsets();
            int hotseatInset = dp.isSeascape() ? targetInsets.left : targetInsets.right;
            return dp.hotseatBarSizePx + hotseatInset;
        } else {
            return dp.heightPx - outRect.bottom;
        }
    }

    /** 4 */
    @Override
    public void onSwipeUpToHomeComplete(RecentsAnimationDeviceState deviceState) {
        onSwipeUpToRecentsComplete();
    }

    /** 5 */
    @Override
    public void onAssistantVisibilityChanged(float visibility) {
        // This class becomes active when the screen is locked.
        // Rather than having it handle assistant visibility changes, the assistant visibility is
        // set to zero prior to this class becoming active.
    }

    /** 6 */
    @Override
    public AnimationFactory prepareRecentsUI(RecentsAnimationDeviceState deviceState,
            boolean activityVisible, Consumer<AnimatorControllerWithResistance> callback) {
        DefaultAnimationFactory factory = new DefaultAnimationFactory(callback);
        factory.initUI();
        return factory;
    }

    @Override
    public ActivityInitListener createActivityInitListener(
            Predicate<Boolean> onInitListener) {
        return new ActivityInitListener<>((activity, alreadyOnHome) ->
                onInitListener.test(alreadyOnHome), RecentsActivity.ACTIVITY_TRACKER);
    }

    @Nullable
    @Override
    public RecentsActivity getCreatedActivity() {
        return RecentsActivity.ACTIVITY_TRACKER.getCreatedActivity();
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        RecentsActivity activity = getCreatedActivity();
        if (activity != null && activity.hasWindowFocus()) {
            return activity.getOverviewPanel();
        }
        return null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Runnable onCompleteCallback) {
        return false;
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target) {
        // TODO: Remove this once b/77875376 is fixed
        return target.screenSpaceBounds;
    }

    @Override
    public boolean allowMinimizeSplitScreen() {
        // TODO: Remove this once b/77875376 is fixed
        return false;
    }

    @Override
    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        // In non-gesture mode, user might be clicking on the home button which would directly
        // start the home activity instead of going through recents. In that case, defer starting
        // recents until we are sure it is a gesture.
        return !deviceState.isFullyGesturalNavMode()
                || super.deferStartingActivity(deviceState, ev);
    }

    @Override
    public void onExitOverview(RotationTouchHelper deviceState, Runnable exitRunnable) {
        // no-op, fake landscape not supported for 3P
    }

    @Override
    public int getContainerType() {
        RecentsActivity activity = getCreatedActivity();
        boolean visible = activity != null && activity.isStarted() && activity.hasWindowFocus();
        return visible
                ? LauncherLogProto.ContainerType.OTHER_LAUNCHER_APP
                : LauncherLogProto.ContainerType.APP;
    }

    @Override
    public boolean isInLiveTileMode() {
        return false;
    }

    @Override
    public void onLaunchTaskFailed() {
        // TODO: probably go back to overview instead.
        RecentsActivity activity = getCreatedActivity();
        if (activity == null) {
            return;
        }
        activity.<RecentsView>getOverviewPanel().startHome();
    }

    @Override
    protected float getExtraSpace(Context context, DeviceProfile dp,
            PagedOrientationHandler orientationHandler) {
        return showOverviewActions(context)
                ? context.getResources().getDimensionPixelSize(R.dimen.overview_actions_height)
                : 0;
    }
}
