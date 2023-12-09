/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.android.app.animation.Interpolators.DECELERATE;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherPrefs.ALL_APPS_OVERVIEW_THRESHOLD;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.FloatProperty;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.AllAppsSwipeController;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.quickstep.views.RecentsView;

/**
 * Controls an animation that can go beyond progress = 1, at which point resistance should be
 * applied. Internally, this is a wrapper around 2 {@link AnimatorPlaybackController}s, one that
 * runs from progress 0 to 1 like normal, then one that seamlessly continues that animation but
 * starts applying resistance as well.
 */
public class AnimatorControllerWithResistance {

    private enum RecentsResistanceParams {
        FROM_APP(0.75f, 0.5f, 1f, false),
        FROM_APP_TO_ALL_APPS(1f, 0.6f, 0.8f, false),
        FROM_APP_TABLET(1f, 0.7f, 1f, true),
        FROM_APP_TO_ALL_APPS_TABLET(1f, 0.5f, 0.5f, false),
        FROM_OVERVIEW(1f, 0.75f, 0.5f, false);

        RecentsResistanceParams(float scaleStartResist, float scaleMaxResist,
                float translationFactor, boolean stopScalingAtTop) {
            this.scaleStartResist = scaleStartResist;
            this.scaleMaxResist = scaleMaxResist;
            this.translationFactor = translationFactor;
            this.stopScalingAtTop = stopScalingAtTop;
        }

        /**
         * Start slowing down the rate of scaling down when recents view is smaller than this scale.
         */
        public final float scaleStartResist;

        /**
         * Recents view will reach this scale at the very end of the drag.
         */
        public final float scaleMaxResist;

        /**
         * How much translation to apply to RecentsView when the drag reaches the top of the screen,
         * where 0 will keep it centered and 1 will have it barely touch the top of the screen.
         */
        public final float translationFactor;

        /**
         * Whether to end scaling effect when the scaled down version of TaskView's top reaches the
         * non-scaled version of TaskView's top.
         */
        public final boolean stopScalingAtTop;
    }

    private static final TimeInterpolator RECENTS_SCALE_RESIST_INTERPOLATOR = DECELERATE;
    private static final TimeInterpolator RECENTS_TRANSLATE_RESIST_INTERPOLATOR = LINEAR;

    private static final Rect TEMP_RECT = new Rect();

    private final AnimatorPlaybackController mNormalController;
    private final AnimatorPlaybackController mResistanceController;

    // Initialize to -1 so the first 0 gets applied.
    private float mLastNormalProgress = -1;
    private float mLastResistProgress;

    public AnimatorControllerWithResistance(AnimatorPlaybackController normalController,
            AnimatorPlaybackController resistanceController) {
        mNormalController = normalController;
        mResistanceController = resistanceController;
    }

    public AnimatorPlaybackController getNormalController() {
        return mNormalController;
    }

    /**
     * Applies the current progress of the animation.
     * @param progress From 0 to maxProgress, where 1 is the target we are animating towards.
     * @param maxProgress > 1, this is where the resistance will be applied.
     */
    public void setProgress(float progress, float maxProgress) {
        float normalProgress = Utilities.boundToRange(progress, 0, 1);
        if (normalProgress != mLastNormalProgress) {
            mLastNormalProgress = normalProgress;
            mNormalController.setPlayFraction(normalProgress);
        }
        if (maxProgress <= 1) {
            return;
        }
        float resistProgress = progress <= 1 ? 0 : Utilities.getProgress(progress, 1, maxProgress);
        if (resistProgress != mLastResistProgress) {
            mLastResistProgress = resistProgress;
            mResistanceController.setPlayFraction(resistProgress);
        }
    }

    /**
     * Applies resistance to recents when swiping up past its target position.
     * @param normalController The controller to run from 0 to 1 before this resistance applies.
     * @param context Used to compute start and end values.
     * @param recentsOrientedState Used to compute start and end values.
     * @param dp Used to compute start and end values.
     * @param scaleTarget The target for the scaleProperty.
     * @param scaleProperty Animate the value to change the scale of the window/recents view.
     * @param translationTarget The target for the translationProperty.
     * @param translationProperty Animate the value to change the translation of the recents view.
     */
    public static <SCALE, TRANSLATION> AnimatorControllerWithResistance createForRecents(
            AnimatorPlaybackController normalController, Context context,
            RecentsOrientedState recentsOrientedState, DeviceProfile dp, SCALE scaleTarget,
            FloatProperty<SCALE> scaleProperty, TRANSLATION translationTarget,
            FloatProperty<TRANSLATION> translationProperty) {

        RecentsParams params = new RecentsParams(context, recentsOrientedState, dp, scaleTarget,
                scaleProperty, translationTarget, translationProperty);
        PendingAnimation resistAnim = createRecentsResistanceAnim(params);

        // Apply All Apps animation during the resistance animation.
        if (recentsOrientedState.getActivityInterface().allowAllAppsFromOverview()) {
            StatefulActivity activity =
                    recentsOrientedState.getActivityInterface().getCreatedActivity();
            if (activity != null) {
                StateManager<LauncherState> stateManager = activity.getStateManager();
                if (stateManager.isInStableState(LauncherState.BACKGROUND_APP)
                        && stateManager.isInTransition()) {

                    // Calculate the resistance progress threshold where All Apps will trigger.
                    float threshold = getAllAppsThreshold(context, recentsOrientedState, dp);

                    StateAnimationConfig config = new StateAnimationConfig();
                    AllAppsSwipeController.applyOverviewToAllAppsAnimConfig(dp, config, threshold);
                    AnimatorSet allAppsAnimator = stateManager.createAnimationToNewWorkspace(
                            LauncherState.ALL_APPS, config).getTarget();
                    resistAnim.add(allAppsAnimator);
                }
            }
        }

        AnimatorPlaybackController resistanceController = resistAnim.createPlaybackController();
        return new AnimatorControllerWithResistance(normalController, resistanceController);
    }

    private static float getAllAppsThreshold(Context context,
            RecentsOrientedState recentsOrientedState, DeviceProfile dp) {
        int transitionDragLength =
                recentsOrientedState.getActivityInterface().getSwipeUpDestinationAndLength(
                        dp, context, TEMP_RECT,
                        recentsOrientedState.getOrientationHandler());
        float dragLengthFactor = (float) dp.heightPx / transitionDragLength;
        // -1s are because 0-1 is reserved for the normal transition.
        float threshold = LauncherPrefs.get(context).get(ALL_APPS_OVERVIEW_THRESHOLD) / 100f;
        return (threshold - 1) / (dragLengthFactor - 1);
    }

    /**
     * Creates the resistance animation for {@link #createForRecents}, or can be used separately
     * when starting from recents, i.e. {@link #createRecentsResistanceFromOverviewAnim}.
     */
    public static <SCALE, TRANSLATION> PendingAnimation createRecentsResistanceAnim(
            RecentsParams<SCALE, TRANSLATION> params) {
        Rect startRect = new Rect();
        PagedOrientationHandler orientationHandler = params.recentsOrientedState
                .getOrientationHandler();
        params.recentsOrientedState.getActivityInterface()
                .calculateTaskSize(params.context, params.dp, startRect, orientationHandler);
        long distanceToCover = startRect.bottom;
        PendingAnimation resistAnim = params.resistAnim != null
                ? params.resistAnim
                : new PendingAnimation(distanceToCover * 2);

        PointF pivot = new PointF();
        float fullscreenScale = params.recentsOrientedState.getFullScreenScaleAndPivot(
                startRect, params.dp, pivot);

        // Compute where the task view would be based on the end scale.
        RectF endRectF = new RectF(startRect);
        Matrix temp = new Matrix();
        temp.setScale(params.resistanceParams.scaleMaxResist,
                params.resistanceParams.scaleMaxResist, pivot.x, pivot.y);
        temp.mapRect(endRectF);
        // Translate such that the task view touches the top of the screen when drag does.
        float endTranslation = endRectF.top
                * orientationHandler.getSecondaryTranslationDirectionFactor()
                * params.resistanceParams.translationFactor;
        resistAnim.addFloat(params.translationTarget, params.translationProperty,
                params.startTranslation, endTranslation, RECENTS_TRANSLATE_RESIST_INTERPOLATOR);

        float prevScaleRate = (fullscreenScale - params.startScale)
                / (params.dp.heightPx - startRect.bottom);
        // This is what the scale would be at the end of the drag if we didn't apply resistance.
        float endScale = params.startScale - prevScaleRate * distanceToCover;
        // Create an interpolator that resists the scale so the scale doesn't get smaller than
        // RECENTS_SCALE_MAX_RESIST.
        float startResist = Utilities.getProgress(params.resistanceParams.scaleStartResist,
                params.startScale, endScale);
        float maxResist = Utilities.getProgress(params.resistanceParams.scaleMaxResist,
                params.startScale, endScale);
        float stopResist =
                params.resistanceParams.stopScalingAtTop ? 1f - startRect.top / endRectF.top : 1f;
        final TimeInterpolator scaleInterpolator = t -> {
            if (t < startResist) {
                return t;
            }
            if (t > stopResist) {
                return maxResist;
            }
            float resistProgress = Utilities.getProgress(t, startResist, stopResist);
            resistProgress = RECENTS_SCALE_RESIST_INTERPOLATOR.getInterpolation(resistProgress);
            return startResist + resistProgress * (maxResist - startResist);
        };
        resistAnim.addFloat(params.scaleTarget, params.scaleProperty, params.startScale, endScale,
                scaleInterpolator);

        return resistAnim;
    }

    /**
     * Helper method to update or create a PendingAnimation suitable for animating
     * a RecentsView interaction that started from the overview state.
     */
    public static PendingAnimation createRecentsResistanceFromOverviewAnim(
            Launcher launcher, @Nullable PendingAnimation resistanceAnim) {
        RecentsView recentsView = launcher.getOverviewPanel();
        RecentsParams params = new RecentsParams(launcher, recentsView.getPagedViewOrientedState(),
                launcher.getDeviceProfile(), recentsView, RECENTS_SCALE_PROPERTY, recentsView,
                TASK_SECONDARY_TRANSLATION)
                .setResistAnim(resistanceAnim)
                .setResistanceParams(RecentsResistanceParams.FROM_OVERVIEW)
                .setStartScale(recentsView.getScaleX());
        return createRecentsResistanceAnim(params);
    }

    /**
     * Params to compute resistance when scaling/translating recents.
     */
    private static class RecentsParams<SCALE, TRANSLATION> {
        // These are all required and can't have default values, hence are final.
        public final Context context;
        public final RecentsOrientedState recentsOrientedState;
        public final DeviceProfile dp;
        public final SCALE scaleTarget;
        public final FloatProperty<SCALE> scaleProperty;
        public final TRANSLATION translationTarget;
        public final FloatProperty<TRANSLATION> translationProperty;

        // These are not required, or can have a default value that is generally correct.
        @Nullable public PendingAnimation resistAnim = null;
        public RecentsResistanceParams resistanceParams;
        public float startScale = 1f;
        public float startTranslation = 0f;

        private RecentsParams(Context context, RecentsOrientedState recentsOrientedState,
                DeviceProfile dp, SCALE scaleTarget, FloatProperty<SCALE> scaleProperty,
                TRANSLATION translationTarget, FloatProperty<TRANSLATION> translationProperty) {
            this.context = context;
            this.recentsOrientedState = recentsOrientedState;
            this.dp = dp;
            this.scaleTarget = scaleTarget;
            this.scaleProperty = scaleProperty;
            this.translationTarget = translationTarget;
            this.translationProperty = translationProperty;
            if (dp.isTablet) {
                resistanceParams =
                        recentsOrientedState.getActivityInterface().allowAllAppsFromOverview()
                                ? RecentsResistanceParams.FROM_APP_TO_ALL_APPS_TABLET
                                : RecentsResistanceParams.FROM_APP_TABLET;
            } else {
                resistanceParams =
                        recentsOrientedState.getActivityInterface().allowAllAppsFromOverview()
                                ? RecentsResistanceParams.FROM_APP_TO_ALL_APPS
                                : RecentsResistanceParams.FROM_APP;
            }
        }

        private RecentsParams setResistAnim(PendingAnimation resistAnim) {
            this.resistAnim = resistAnim;
            return this;
        }

        private RecentsParams setResistanceParams(RecentsResistanceParams resistanceParams) {
            this.resistanceParams = resistanceParams;
            return this;
        }

        private RecentsParams setStartScale(float startScale) {
            this.startScale = startScale;
            return this;
        }

        private RecentsParams setStartTranslation(float startTranslation) {
            this.startTranslation = startTranslation;
            return this;
        }
    }
}
