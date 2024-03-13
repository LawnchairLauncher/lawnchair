/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherState.CLEAR_ALL_BUTTON;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_ACTIONS;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_ACTIONS_FADE;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.quickstep.views.RecentsView.CONTENT_ALPHA;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.TASK_MODALNESS;
import static com.android.quickstep.views.RecentsView.TASK_PRIMARY_SPLIT_TRANSLATION;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_SPLIT_TRANSLATION;
import static com.android.quickstep.views.TaskView.FLAG_UPDATE_ALL;
import static com.android.wm.shell.Flags.enableSplitContextual;

import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.util.AnimUtils;
import com.android.quickstep.util.SplitAnimationTimings;
import com.android.quickstep.views.ClearAllButton;
import com.android.quickstep.views.LauncherRecentsView;
import com.android.quickstep.views.RecentsView;

/**
 * State handler for handling UI changes for {@link LauncherRecentsView}. In addition to managing
 * the basic view properties, this class also manages changes in the task visuals.
 */
@TargetApi(Build.VERSION_CODES.O)
public final class RecentsViewStateController extends
        BaseRecentsViewStateController<LauncherRecentsView> {

    public RecentsViewStateController(QuickstepLauncher launcher) {
        super(launcher);
    }

    @Override
    public void setState(@NonNull LauncherState state) {
        super.setState(state);
        if (state.overviewUi) {
            mRecentsView.updateEmptyMessage();
        } else {
            mRecentsView.resetTaskVisuals();
        }
        setAlphas(PropertySetter.NO_ANIM_PROPERTY_SETTER, new StateAnimationConfig(), state);
        mRecentsView.setFullscreenProgress(state.getOverviewFullscreenProgress());
        // In Overview, we may be layering app surfaces behind Launcher, so we need to notify
        // DepthController to prevent optimizations which might occlude the layers behind
        mLauncher.getDepthController().setHasContentBehindLauncher(state.overviewUi);

        PendingAnimation builder =
                new PendingAnimation(state.getTransitionDuration(mLauncher, true));

        handleSplitSelectionState(state, builder, /* animate */false);
    }

    @Override
    void setStateWithAnimationInternal(@NonNull LauncherState toState,
            @NonNull StateAnimationConfig config, @NonNull PendingAnimation builder) {
        super.setStateWithAnimationInternal(toState, config, builder);

        if (toState.overviewUi) {
            // While animating into recents, update the visible task data as needed
            builder.addOnFrameCallback(() -> mRecentsView.loadVisibleTaskData(FLAG_UPDATE_ALL));
            mRecentsView.updateEmptyMessage();
            // TODO(b/246283207): Remove logging once root cause of flake detected.
            if (Utilities.isRunningInTestHarness()) {
                Log.d("b/246283207", "RecentsView#setStateWithAnimationInternal getCurrentPage(): "
                                + mRecentsView.getCurrentPage()
                                + ", getScrollForPage(getCurrentPage())): "
                                + mRecentsView.getScrollForPage(mRecentsView.getCurrentPage()));
            }
        } else {
            builder.addListener(
                    AnimatorListeners.forSuccessCallback(mRecentsView::resetTaskVisuals));
        }
        // In Overview, we may be layering app surfaces behind Launcher, so we need to notify
        // DepthController to prevent optimizations which might occlude the layers behind
        builder.addListener(AnimatorListeners.forSuccessCallback(() ->
                mLauncher.getDepthController().setHasContentBehindLauncher(toState.overviewUi)));

        handleSplitSelectionState(toState, builder, /* animate */true);

        setAlphas(builder, config, toState);
        builder.setFloat(mRecentsView, FULLSCREEN_PROGRESS,
                toState.getOverviewFullscreenProgress(), LINEAR);
    }

    /**
     * Create or dismiss split screen select animations.
     * @param builder if null then this will run the split select animations right away, otherwise
     *                will add animations to builder.
     */
    private void handleSplitSelectionState(@NonNull LauncherState toState,
            @NonNull PendingAnimation builder, boolean animate) {
        boolean goingToOverviewFromWorkspaceContextual = enableSplitContextual() &&
                toState == OVERVIEW && mLauncher.isSplitSelectionActive();
        if (toState != OVERVIEW_SPLIT_SELECT && !goingToOverviewFromWorkspaceContextual) {
            // Not going to split
            return;
        }

        // Create transition animations to split select
        RecentsPagedOrientationHandler orientationHandler =
                ((RecentsView) mLauncher.getOverviewPanel()).getPagedOrientationHandler();
        Pair<FloatProperty<RecentsView>, FloatProperty<RecentsView>> taskViewsFloat =
                orientationHandler.getSplitSelectTaskOffset(
                        TASK_PRIMARY_SPLIT_TRANSLATION, TASK_SECONDARY_SPLIT_TRANSLATION,
                        mLauncher.getDeviceProfile());

        SplitAnimationTimings timings =
                AnimUtils.getDeviceOverviewToSplitTimings(mLauncher.getDeviceProfile().isTablet);
        if (!goingToOverviewFromWorkspaceContextual) {
            // This animation is already done for the contextual case, don't redo it
            mRecentsView.createSplitSelectInitAnimation(builder,
                    toState.getTransitionDuration(mLauncher, true /* isToState */));
        }
        // Shift tasks vertically downward to get out of placeholder view
        builder.setFloat(mRecentsView, taskViewsFloat.first,
                toState.getSplitSelectTranslation(mLauncher),
                timings.getGridSlidePrimaryInterpolator());
        // Zero out horizontal translation
        builder.setFloat(mRecentsView, taskViewsFloat.second,
                0,
                timings.getGridSlideSecondaryInterpolator());

        if (!animate) {
            AnimatorSet as = builder.buildAnim();
            as.start();
            as.end();
        }
    }

    private void setAlphas(PropertySetter propertySetter, StateAnimationConfig config,
            LauncherState state) {
        float clearAllButtonAlpha = state.areElementsVisible(mLauncher, CLEAR_ALL_BUTTON) ? 1 : 0;
        propertySetter.setFloat(mRecentsView.getClearAllButton(), ClearAllButton.VISIBILITY_ALPHA,
                clearAllButtonAlpha, LINEAR);
        float overviewButtonAlpha = state.areElementsVisible(mLauncher, OVERVIEW_ACTIONS) ? 1 : 0;
        propertySetter.setFloat(mLauncher.getActionsView().getVisibilityAlpha(),
                MULTI_PROPERTY_VALUE, overviewButtonAlpha, config.getInterpolator(
                        ANIM_OVERVIEW_ACTIONS_FADE, LINEAR));
    }

    @Override
    FloatProperty<RecentsView> getTaskModalnessProperty() {
        return TASK_MODALNESS;
    }

    @Override
    FloatProperty<RecentsView> getContentAlphaProperty() {
        return CONTENT_ALPHA;
    }
}
