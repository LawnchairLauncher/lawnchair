/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.BaseActivity.INVISIBLE_ALL;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_APP_TRANSITIONS;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_PENDING_FLAGS;
import static com.android.launcher3.BaseActivity.PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE;
import static com.android.launcher3.anim.Interpolators.DEACCEL_1_7;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.dragndrop.DragLayer.ALPHA_INDEX_TRANSITIONS;
import static com.android.quickstep.TaskUtils.findTaskViewToLaunch;
import static com.android.quickstep.TaskUtils.getRecentsWindowAnimator;
import static com.android.quickstep.TaskUtils.taskIsATargetWithMode;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.InsettableFrameLayout.LayoutParams;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.ActivityCompat;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationDefinitionCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplier;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplier.SurfaceParams;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * Manages the opening and closing app transitions from Launcher.
 */
@TargetApi(Build.VERSION_CODES.O)
@SuppressWarnings("unused")
public class LauncherAppTransitionManagerImpl extends LauncherAppTransitionManager
        implements OnDeviceProfileChangeListener {

    private static final String TAG = "LauncherTransition";

    /** Duration of status bar animations. */
    public static final int STATUS_BAR_TRANSITION_DURATION = 120;

    /**
     * Since our animations decelerate heavily when finishing, we want to start status bar animations
     * x ms before the ending.
     */
    public static final int STATUS_BAR_TRANSITION_PRE_DELAY = 96;

    private static final String CONTROL_REMOTE_APP_TRANSITION_PERMISSION =
            "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS";

    private static final int APP_LAUNCH_DURATION = 500;
    // Use a shorter duration for x or y translation to create a curve effect
    private static final int APP_LAUNCH_CURVED_DURATION = APP_LAUNCH_DURATION / 2;
    // We scale the durations for the downward app launch animations (minus the scale animation).
    private static final float APP_LAUNCH_DOWN_DUR_SCALE_FACTOR = 0.8f;
    private static final int APP_LAUNCH_ALPHA_START_DELAY = 32;
    private static final int APP_LAUNCH_ALPHA_DURATION = 50;

    public static final int RECENTS_LAUNCH_DURATION = 336;
    public static final int RECENTS_QUICKSCRUB_LAUNCH_DURATION = 300;
    private static final int LAUNCHER_RESUME_START_DELAY = 100;
    private static final int CLOSING_TRANSITION_DURATION_MS = 250;

    // Progress = 0: All apps is fully pulled up, Progress = 1: All apps is fully pulled down.
    public static final float ALL_APPS_PROGRESS_OFF_SCREEN = 1.3059858f;

    private final Launcher mLauncher;
    private final DragLayer mDragLayer;
    private final AlphaProperty mDragLayerAlpha;

    private final Handler mHandler;
    private final boolean mIsRtl;

    private final float mContentTransY;
    private final float mWorkspaceTransY;
    private final float mClosingWindowTransY;

    private DeviceProfile mDeviceProfile;
    private View mFloatingView;

    private RemoteAnimationProvider mRemoteAnimationProvider;

    private final AnimatorListenerAdapter mForceInvisibleListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            mLauncher.addForceInvisibleFlag(INVISIBLE_BY_APP_TRANSITIONS);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mLauncher.clearForceInvisibleFlag(INVISIBLE_BY_APP_TRANSITIONS);
        }
    };

    public LauncherAppTransitionManagerImpl(Context context) {
        mLauncher = Launcher.getLauncher(context);
        mDragLayer = mLauncher.getDragLayer();
        mDragLayerAlpha = mDragLayer.getAlphaProperty(ALPHA_INDEX_TRANSITIONS);
        mHandler = new Handler(Looper.getMainLooper());
        mIsRtl = Utilities.isRtl(mLauncher.getResources());
        mDeviceProfile = mLauncher.getDeviceProfile();

        Resources res = mLauncher.getResources();
        mContentTransY = res.getDimensionPixelSize(R.dimen.content_trans_y);
        mWorkspaceTransY = res.getDimensionPixelSize(R.dimen.workspace_trans_y);
        mClosingWindowTransY = res.getDimensionPixelSize(R.dimen.closing_window_trans_y);

        mLauncher.addOnDeviceProfileChangeListener(this);
        registerRemoteAnimations();
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mDeviceProfile = dp;
    }

    /**
     * @return ActivityOptions with remote animations that controls how the window of the opening
     *         targets are displayed.
     */
    @Override
    public ActivityOptions getActivityLaunchOptions(Launcher launcher, View v) {
        if (hasControlRemoteAppTransitionPermission()) {
            RemoteAnimationRunnerCompat runner = new LauncherAnimationRunner(mHandler,
                    true /* startAtFrontOfQueue */) {

                @Override
                public void onCreateAnimation(RemoteAnimationTargetCompat[] targetCompats,
                        AnimationResult result) {
                    AnimatorSet anim = new AnimatorSet();

                    boolean launcherClosing =
                            launcherIsATargetWithMode(targetCompats, MODE_CLOSING);

                    if (!composeRecentsLaunchAnimator(v, targetCompats, anim)) {
                        // Set the state animation first so that any state listeners are called
                        // before our internal listeners.
                        mLauncher.getStateManager().setCurrentAnimation(anim);

                        Rect windowTargetBounds = getWindowTargetBounds(targetCompats);
                        playIconAnimators(anim, v, windowTargetBounds);
                        if (launcherClosing) {
                            Pair<AnimatorSet, Runnable> launcherContentAnimator =
                                    getLauncherContentAnimator(true /* isAppOpening */);
                            anim.play(launcherContentAnimator.first);
                            anim.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    launcherContentAnimator.second.run();
                                }
                            });
                        }
                        anim.play(getOpeningWindowAnimators(v, targetCompats, windowTargetBounds));
                    }

                    if (launcherClosing) {
                        anim.addListener(mForceInvisibleListener);
                    }

                    result.setAnimation(anim);
                }
            };

            boolean fromRecents = mLauncher.getStateManager().getState().overviewUi
                    && findTaskViewToLaunch(launcher, v, null) != null;
            int duration = fromRecents
                    ? RECENTS_LAUNCH_DURATION
                    : APP_LAUNCH_DURATION;

            int statusBarTransitionDelay = duration - STATUS_BAR_TRANSITION_DURATION
                    - STATUS_BAR_TRANSITION_PRE_DELAY;
            return ActivityOptionsCompat.makeRemoteAnimation(new RemoteAnimationAdapterCompat(
                    runner, duration, statusBarTransitionDelay));
        }
        return super.getActivityLaunchOptions(launcher, v);
    }

    /**
     * Return the window bounds of the opening target.
     * In multiwindow mode, we need to get the final size of the opening app window target to help
     * figure out where the floating view should animate to.
     */
    private Rect getWindowTargetBounds(RemoteAnimationTargetCompat[] targets) {
        Rect bounds = new Rect(0, 0, mDeviceProfile.widthPx, mDeviceProfile.heightPx);
        if (mLauncher.isInMultiWindowModeCompat()) {
            for (RemoteAnimationTargetCompat target : targets) {
                if (target.mode == MODE_OPENING) {
                    bounds.set(target.sourceContainerBounds);
                    bounds.offsetTo(target.position.x, target.position.y);
                    return bounds;
                }
            }
        }
        return bounds;
    }

    public void setRemoteAnimationProvider(final RemoteAnimationProvider animationProvider,
            CancellationSignal cancellationSignal) {
        mRemoteAnimationProvider = animationProvider;
        cancellationSignal.setOnCancelListener(() -> {
            if (animationProvider == mRemoteAnimationProvider) {
                mRemoteAnimationProvider = null;
            }
        });
    }

    /**
     * Composes the animations for a launch from the recents list if possible.
     */
    private boolean composeRecentsLaunchAnimator(View v,
            RemoteAnimationTargetCompat[] targets, AnimatorSet target) {
        // Ensure recents is actually visible
        if (!mLauncher.getStateManager().getState().overviewUi) {
            return false;
        }

        RecentsView recentsView = mLauncher.getOverviewPanel();
        boolean launcherClosing = launcherIsATargetWithMode(targets, MODE_CLOSING);
        boolean skipLauncherChanges = !launcherClosing;
        boolean isLaunchingFromQuickscrub =
                recentsView.getQuickScrubController().isWaitingForTaskLaunch();

        TaskView taskView = findTaskViewToLaunch(mLauncher, v, targets);
        if (taskView == null) {
            return false;
        }

        int duration = isLaunchingFromQuickscrub
                ? RECENTS_QUICKSCRUB_LAUNCH_DURATION
                : RECENTS_LAUNCH_DURATION;

        ClipAnimationHelper helper = new ClipAnimationHelper();
        target.play(getRecentsWindowAnimator(taskView, skipLauncherChanges, targets, helper)
                .setDuration(duration));

        Animator childStateAnimation = null;
        // Found a visible recents task that matches the opening app, lets launch the app from there
        Animator launcherAnim;
        final AnimatorListenerAdapter windowAnimEndListener;
        if (launcherClosing) {
            launcherAnim = recentsView.createAdjacentPageAnimForTaskLaunch(taskView, helper);
            launcherAnim.setInterpolator(Interpolators.TOUCH_RESPONSE_INTERPOLATOR);
            launcherAnim.setDuration(duration);

            // Make sure recents gets fixed up by resetting task alphas and scales, etc.
            windowAnimEndListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLauncher.getStateManager().moveToRestState();
                    mLauncher.getStateManager().reapplyState();
                }
            };
        } else {
            AnimatorPlaybackController controller =
                    mLauncher.getStateManager().createAnimationToNewWorkspace(NORMAL, duration);
            controller.dispatchOnStart();
            childStateAnimation = controller.getTarget();
            launcherAnim = controller.getAnimationPlayer().setDuration(duration);
            windowAnimEndListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLauncher.getStateManager().goToState(NORMAL, false);
                }
            };
        }
        target.play(launcherAnim);

        // Set the current animation first, before adding windowAnimEndListener. Setting current
        // animation adds some listeners which need to be called before windowAnimEndListener
        // (the ordering of listeners matter in this case).
        mLauncher.getStateManager().setCurrentAnimation(target, childStateAnimation);
        target.addListener(windowAnimEndListener);
        return true;
    }

    /**
     * Content is everything on screen except the background and the floating view (if any).
     *
     * @param isAppOpening True when this is called when an app is opening.
     *                     False when this is called when an app is closing.
     */
    private Pair<AnimatorSet, Runnable> getLauncherContentAnimator(boolean isAppOpening) {
        AnimatorSet launcherAnimator = new AnimatorSet();
        Runnable endListener;

        float[] alphas = isAppOpening
                ? new float[] {1, 0}
                : new float[] {0, 1};
        float[] trans = isAppOpening
                ? new float[] {0, mContentTransY}
                : new float[] {-mContentTransY, 0};

        if (mLauncher.isInState(ALL_APPS)) {
            // All Apps in portrait mode is full screen, so we only animate AllAppsContainerView.
            final View appsView = mLauncher.getAppsView();
            final float startAlpha = appsView.getAlpha();
            final float startY = appsView.getTranslationY();
            appsView.setAlpha(alphas[0]);
            appsView.setTranslationY(trans[0]);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(appsView, View.ALPHA, alphas);
            alpha.setDuration(217);
            alpha.setInterpolator(LINEAR);
            appsView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            alpha.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    appsView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
            });
            ObjectAnimator transY = ObjectAnimator.ofFloat(appsView, View.TRANSLATION_Y, trans);
            transY.setInterpolator(AGGRESSIVE_EASE);
            transY.setDuration(350);

            launcherAnimator.play(alpha);
            launcherAnimator.play(transY);

            endListener = () -> {
                appsView.setAlpha(startAlpha);
                appsView.setTranslationY(startY);
                appsView.setLayerType(View.LAYER_TYPE_NONE, null);
            };
        } else if (mLauncher.isInState(OVERVIEW)) {
            AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
            launcherAnimator.play(ObjectAnimator.ofFloat(allAppsController, ALL_APPS_PROGRESS,
                    allAppsController.getProgress(), ALL_APPS_PROGRESS_OFF_SCREEN));

            RecentsView overview = mLauncher.getOverviewPanel();
            ObjectAnimator alpha = ObjectAnimator.ofFloat(overview,
                    RecentsView.CONTENT_ALPHA, alphas);
            alpha.setDuration(217);
            alpha.setInterpolator(LINEAR);
            launcherAnimator.play(alpha);

            ObjectAnimator transY = ObjectAnimator.ofFloat(overview, View.TRANSLATION_Y, trans);
            transY.setInterpolator(AGGRESSIVE_EASE);
            transY.setDuration(350);
            launcherAnimator.play(transY);

            endListener = mLauncher.getStateManager()::reapplyState;
        } else {
            mDragLayerAlpha.setValue(alphas[0]);
            ObjectAnimator alpha =
                    ObjectAnimator.ofFloat(mDragLayerAlpha, MultiValueAlpha.VALUE, alphas);
            alpha.setDuration(217);
            alpha.setInterpolator(LINEAR);
            launcherAnimator.play(alpha);

            mDragLayer.setTranslationY(trans[0]);
            ObjectAnimator transY = ObjectAnimator.ofFloat(mDragLayer, View.TRANSLATION_Y, trans);
            transY.setInterpolator(AGGRESSIVE_EASE);
            transY.setDuration(350);
            launcherAnimator.play(transY);

            mDragLayer.getScrim().hideSysUiScrim(true);
            // Pause page indicator animations as they lead to layer trashing.
            mLauncher.getWorkspace().getPageIndicator().pauseAnimations();
            mDragLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            endListener = this::resetContentView;
        }
        return new Pair<>(launcherAnimator, endListener);
    }

    /**
     * Animators for the "floating view" of the view used to launch the target.
     */
    private void playIconAnimators(AnimatorSet appOpenAnimator, View v, Rect windowTargetBounds) {
        final boolean isBubbleTextView = v instanceof BubbleTextView;
        mFloatingView = new View(mLauncher);
        if (isBubbleTextView && v.getTag() instanceof ItemInfoWithIcon ) {
            // Create a copy of the app icon
            mFloatingView.setBackground(
                    DrawableFactory.get(mLauncher).newIcon((ItemInfoWithIcon) v.getTag()));
        }

        // Position the floating view exactly on top of the original
        Rect rect = new Rect();
        final boolean fromDeepShortcutView = v.getParent() instanceof DeepShortcutView;
        if (fromDeepShortcutView) {
            // Deep shortcut views have their icon drawn in a separate view.
            DeepShortcutView view = (DeepShortcutView) v.getParent();
            mDragLayer.getDescendantRectRelativeToSelf(view.getIconView(), rect);
        } else {
            mDragLayer.getDescendantRectRelativeToSelf(v, rect);
        }
        int viewLocationLeft = rect.left;
        int viewLocationTop = rect.top;

        float startScale = 1f;
        if (isBubbleTextView && !fromDeepShortcutView) {
            BubbleTextView btv = (BubbleTextView) v;
            btv.getIconBounds(rect);
            Drawable dr = btv.getIcon();
            if (dr instanceof FastBitmapDrawable) {
                startScale = ((FastBitmapDrawable) dr).getAnimatedScale();
            }
        } else {
            rect.set(0, 0, rect.width(), rect.height());
        }
        viewLocationLeft += rect.left;
        viewLocationTop += rect.top;
        int viewLocationStart = mIsRtl
                ? windowTargetBounds.width() - rect.right
                : viewLocationLeft;
        LayoutParams lp = new LayoutParams(rect.width(), rect.height());
        lp.ignoreInsets = true;
        lp.setMarginStart(viewLocationStart);
        lp.topMargin = viewLocationTop;
        mFloatingView.setLayoutParams(lp);

        // Set the properties here already to make sure they'are available when running the first
        // animation frame.
        mFloatingView.setLeft(viewLocationLeft);
        mFloatingView.setTop(viewLocationTop);
        mFloatingView.setRight(viewLocationLeft + rect.width());
        mFloatingView.setBottom(viewLocationTop + rect.height());

        // Swap the two views in place.
        ((ViewGroup) mDragLayer.getParent()).addView(mFloatingView);
        v.setVisibility(View.INVISIBLE);

        int[] dragLayerBounds = new int[2];
        mDragLayer.getLocationOnScreen(dragLayerBounds);

        // Animate the app icon to the center of the window bounds in screen coordinates.
        float centerX = windowTargetBounds.centerX() - dragLayerBounds[0];
        float centerY = windowTargetBounds.centerY() - dragLayerBounds[1];

        float xPosition = mIsRtl
                ? windowTargetBounds.width() - lp.getMarginStart() - rect.width()
                : lp.getMarginStart();
        float dX = centerX - xPosition - (lp.width / 2);
        float dY = centerY - lp.topMargin - (lp.height / 2);

        ObjectAnimator x = ObjectAnimator.ofFloat(mFloatingView, View.TRANSLATION_X, 0f, dX);
        ObjectAnimator y = ObjectAnimator.ofFloat(mFloatingView, View.TRANSLATION_Y, 0f, dY);

        // Use upward animation for apps that are either on the bottom half of the screen, or are
        // relatively close to the center.
        boolean useUpwardAnimation = lp.topMargin > centerY
                || Math.abs(dY) < mLauncher.getDeviceProfile().cellHeightPx;
        if (useUpwardAnimation) {
            x.setDuration(APP_LAUNCH_CURVED_DURATION);
            y.setDuration(APP_LAUNCH_DURATION);
        } else {
            x.setDuration((long) (APP_LAUNCH_DOWN_DUR_SCALE_FACTOR * APP_LAUNCH_DURATION));
            y.setDuration((long) (APP_LAUNCH_DOWN_DUR_SCALE_FACTOR * APP_LAUNCH_CURVED_DURATION));
        }
        x.setInterpolator(AGGRESSIVE_EASE);
        y.setInterpolator(AGGRESSIVE_EASE);
        appOpenAnimator.play(x);
        appOpenAnimator.play(y);

        // Scale the app icon to take up the entire screen. This simplifies the math when
        // animating the app window position / scale.
        float maxScaleX = windowTargetBounds.width() / (float) rect.width();
        float maxScaleY = windowTargetBounds.height() / (float) rect.height();
        float scale = Math.max(maxScaleX, maxScaleY);
        ObjectAnimator scaleAnim = ObjectAnimator
                .ofFloat(mFloatingView, SCALE_PROPERTY, startScale, scale);
        scaleAnim.setDuration(APP_LAUNCH_DURATION)
                .setInterpolator(Interpolators.EXAGGERATED_EASE);
        appOpenAnimator.play(scaleAnim);

        // Fade out the app icon.
        ObjectAnimator alpha = ObjectAnimator.ofFloat(mFloatingView, View.ALPHA, 1f, 0f);
        if (useUpwardAnimation) {
            alpha.setStartDelay(APP_LAUNCH_ALPHA_START_DELAY);
            alpha.setDuration(APP_LAUNCH_ALPHA_DURATION);
        } else {
            alpha.setStartDelay((long) (APP_LAUNCH_DOWN_DUR_SCALE_FACTOR
                    * APP_LAUNCH_ALPHA_START_DELAY));
            alpha.setDuration((long) (APP_LAUNCH_DOWN_DUR_SCALE_FACTOR * APP_LAUNCH_ALPHA_DURATION));
        }
        alpha.setInterpolator(LINEAR);
        appOpenAnimator.play(alpha);

        appOpenAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Reset launcher to normal state
                v.setVisibility(View.VISIBLE);
                ((ViewGroup) mDragLayer.getParent()).removeView(mFloatingView);
            }
        });
    }

    /**
     * @return Animator that controls the window of the opening targets.
     */
    private ValueAnimator getOpeningWindowAnimators(View v, RemoteAnimationTargetCompat[] targets,
            Rect windowTargetBounds) {
        Rect bounds = new Rect();
        if (v.getParent() instanceof DeepShortcutView) {
            // Deep shortcut views have their icon drawn in a separate view.
            DeepShortcutView view = (DeepShortcutView) v.getParent();
            mDragLayer.getDescendantRectRelativeToSelf(view.getIconView(), bounds);
        } else if (v instanceof BubbleTextView) {
            ((BubbleTextView) v).getIconBounds(bounds);
        } else {
            mDragLayer.getDescendantRectRelativeToSelf(v, bounds);
        }
        int[] floatingViewBounds = new int[2];

        Rect crop = new Rect();
        Matrix matrix = new Matrix();

        RemoteAnimationTargetSet openingTargets = new RemoteAnimationTargetSet(targets,
                MODE_OPENING);
        RemoteAnimationTargetSet closingTargets = new RemoteAnimationTargetSet(targets,
                MODE_CLOSING);
        SyncRtSurfaceTransactionApplier surfaceApplier = new SyncRtSurfaceTransactionApplier(
                mFloatingView);

        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setDuration(APP_LAUNCH_DURATION);
        appAnimator.addUpdateListener(new MultiValueUpdateListener() {
            // Fade alpha for the app window.
            FloatProp mAlpha = new FloatProp(0f, 1f, 0, 60, LINEAR);

            @Override
            public void onUpdate(float percent) {
                final float easePercent = AGGRESSIVE_EASE.getInterpolation(percent);

                // Calculate app icon size.
                float iconWidth = bounds.width() * mFloatingView.getScaleX();
                float iconHeight = bounds.height() * mFloatingView.getScaleY();

                // Scale the app window to match the icon size.
                float scaleX = iconWidth / windowTargetBounds.width();
                float scaleY = iconHeight / windowTargetBounds.height();
                float scale = Math.min(1f, Math.min(scaleX, scaleY));

                // Position the scaled window on top of the icon
                int windowWidth = windowTargetBounds.width();
                int windowHeight = windowTargetBounds.height();
                float scaledWindowWidth = windowWidth * scale;
                float scaledWindowHeight = windowHeight * scale;

                float offsetX = (scaledWindowWidth - iconWidth) / 2;
                float offsetY = (scaledWindowHeight - iconHeight) / 2;
                mFloatingView.getLocationOnScreen(floatingViewBounds);

                float transX0 = floatingViewBounds[0] - offsetX;
                float transY0 = floatingViewBounds[1] - offsetY;

                // Animate the window crop so that it starts off as a square, and then reveals
                // horizontally.
                float cropHeight = windowHeight * easePercent + windowWidth * (1 - easePercent);
                float initialTop = (windowHeight - windowWidth) / 2f;
                crop.left = 0;
                crop.top = (int) (initialTop * (1 - easePercent));
                crop.right = windowWidth;
                crop.bottom = (int) (crop.top + cropHeight);

                SurfaceParams[] params = new SurfaceParams[targets.length];
                for (int i = targets.length - 1; i >= 0; i--) {
                    RemoteAnimationTargetCompat target = targets[i];

                    Rect targetCrop;
                    float alpha;
                    if (target.mode == MODE_OPENING) {
                        matrix.setScale(scale, scale);
                        matrix.postTranslate(transX0, transY0);
                        targetCrop = crop;
                        alpha = mAlpha.value;
                    } else {
                        matrix.setTranslate(target.position.x, target.position.y);
                        alpha = 1f;
                        targetCrop = target.sourceContainerBounds;
                    }

                    params[i] = new SurfaceParams(target.leash, alpha, matrix, targetCrop,
                            RemoteAnimationProvider.getLayer(target, MODE_OPENING));
                }
                surfaceApplier.scheduleApply(params);
            }
        });
        return appAnimator;
    }

    /**
     * Registers remote animations used when closing apps to home screen.
     */
    private void registerRemoteAnimations() {
        // Unregister this
        if (hasControlRemoteAppTransitionPermission()) {
            RemoteAnimationDefinitionCompat definition = new RemoteAnimationDefinitionCompat();
            definition.addRemoteAnimation(WindowManagerWrapper.TRANSIT_WALLPAPER_OPEN,
                    WindowManagerWrapper.ACTIVITY_TYPE_STANDARD,
                    new RemoteAnimationAdapterCompat(getWallpaperOpenRunner(),
                            CLOSING_TRANSITION_DURATION_MS, 0 /* statusBarTransitionDelay */));

            // TODO: Transition for unlock to home TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER
            new ActivityCompat(mLauncher).registerRemoteAnimations(definition);
        }
    }

    private boolean launcherIsATargetWithMode(RemoteAnimationTargetCompat[] targets, int mode) {
        return taskIsATargetWithMode(targets, mLauncher.getTaskId(), mode);
    }

    /**
     * @return Runner that plays when user goes to Launcher
     *         ie. pressing home, swiping up from nav bar.
     */
    private RemoteAnimationRunnerCompat getWallpaperOpenRunner() {
        return new LauncherAnimationRunner(mHandler, false /* startAtFrontOfQueue */) {
            @Override
            public void onCreateAnimation(RemoteAnimationTargetCompat[] targetCompats,
                    AnimationResult result) {
                if (!mLauncher.hasBeenResumed()) {
                    // If launcher is not resumed, wait until new async-frame after resume
                    mLauncher.setOnResumeCallback(() ->
                            postAsyncCallback(mHandler, () ->
                                    onCreateAnimation(targetCompats, result)));
                    return;
                }

                if (mLauncher.hasSomeInvisibleFlag(PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION)) {
                    mLauncher.addForceInvisibleFlag(INVISIBLE_BY_PENDING_FLAGS);
                    mLauncher.getStateManager().moveToRestState();
                }

                AnimatorSet anim = null;
                RemoteAnimationProvider provider = mRemoteAnimationProvider;
                if (provider != null) {
                    anim = provider.createWindowAnimation(targetCompats);
                }

                if (anim == null) {
                    anim = new AnimatorSet();
                    anim.play(getClosingWindowAnimators(targetCompats));

                    // Normally, we run the launcher content animation when we are transitioning
                    // home, but if home is already visible, then we don't want to animate the
                    // contents of launcher unless we know that we are animating home as a result
                    // of the home button press with quickstep, which will result in launcher being
                    // started on touch down, prior to the animation home (and won't be in the
                    // targets list because it is already visible). In that case, we force
                    // invisibility on touch down, and only reset it after the animation to home
                    // is initialized.
                    if (launcherIsATargetWithMode(targetCompats, MODE_OPENING)
                            || mLauncher.isForceInvisible()) {
                        // Only register the content animation for cancellation when state changes
                        mLauncher.getStateManager().setCurrentAnimation(anim);
                        createLauncherResumeAnimation(anim);
                    }
                }

                mLauncher.clearForceInvisibleFlag(INVISIBLE_ALL);
                result.setAnimation(anim);
            }
        };
    }

    /**
     * Animator that controls the transformations of the windows the targets that are closing.
     */
    private Animator getClosingWindowAnimators(RemoteAnimationTargetCompat[] targets) {
        SyncRtSurfaceTransactionApplier surfaceApplier =
                new SyncRtSurfaceTransactionApplier(mDragLayer);
        Matrix matrix = new Matrix();
        ValueAnimator closingAnimator = ValueAnimator.ofFloat(0, 1);
        int duration = CLOSING_TRANSITION_DURATION_MS;
        closingAnimator.setDuration(duration);
        closingAnimator.addUpdateListener(new MultiValueUpdateListener() {
            FloatProp mDy = new FloatProp(0, mClosingWindowTransY, 0, duration, DEACCEL_1_7);
            FloatProp mScale = new FloatProp(1f, 1f, 0, duration, DEACCEL_1_7);
            FloatProp mAlpha = new FloatProp(1f, 0f, 25, 125, LINEAR);

            @Override
            public void onUpdate(float percent) {
                SurfaceParams[] params = new SurfaceParams[targets.length];
                for (int i = targets.length - 1; i >= 0; i--) {
                    RemoteAnimationTargetCompat target = targets[i];
                    float alpha;
                    if (target.mode == MODE_CLOSING) {
                        matrix.setScale(mScale.value, mScale.value,
                                target.sourceContainerBounds.centerX(),
                                target.sourceContainerBounds.centerY());
                        matrix.postTranslate(0, mDy.value);
                        matrix.postTranslate(target.position.x, target.position.y);
                        alpha = mAlpha.value;
                    } else {
                        matrix.setTranslate(target.position.x, target.position.y);
                        alpha = 1f;
                    }
                    params[i] = new SurfaceParams(target.leash, alpha, matrix,
                            target.sourceContainerBounds,
                            RemoteAnimationProvider.getLayer(target, MODE_CLOSING));
                }
                surfaceApplier.scheduleApply(params);
            }
        });

        return closingAnimator;
    }

    /**
     * Creates an animator that modifies Launcher as a result from {@link #getWallpaperOpenRunner}.
     */
    private void createLauncherResumeAnimation(AnimatorSet anim) {
        if (mLauncher.isInState(LauncherState.ALL_APPS)) {
            Pair<AnimatorSet, Runnable> contentAnimator =
                    getLauncherContentAnimator(false /* isAppOpening */);
            contentAnimator.first.setStartDelay(LAUNCHER_RESUME_START_DELAY);
            anim.play(contentAnimator.first);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    contentAnimator.second.run();
                }
            });
        } else {
            AnimatorSet workspaceAnimator = new AnimatorSet();

            mDragLayer.setTranslationY(-mWorkspaceTransY);;
            workspaceAnimator.play(ObjectAnimator.ofFloat(mDragLayer, View.TRANSLATION_Y,
                    -mWorkspaceTransY, 0));

            mDragLayerAlpha.setValue(0);
            workspaceAnimator.play(ObjectAnimator.ofFloat(
                    mDragLayerAlpha, MultiValueAlpha.VALUE, 0, 1f));

            workspaceAnimator.setStartDelay(LAUNCHER_RESUME_START_DELAY);
            workspaceAnimator.setDuration(333);
            workspaceAnimator.setInterpolator(Interpolators.DEACCEL_1_7);

            mDragLayer.getScrim().hideSysUiScrim(true);

            // Pause page indicator animations as they lead to layer trashing.
            mLauncher.getWorkspace().getPageIndicator().pauseAnimations();
            mDragLayer.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            workspaceAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    resetContentView();
                }
            });
            anim.play(workspaceAnimator);
        }
    }

    private void resetContentView() {
        mLauncher.getWorkspace().getPageIndicator().skipAnimationsToEnd();
        mDragLayerAlpha.setValue(1f);
        mDragLayer.setLayerType(View.LAYER_TYPE_NONE, null);
        mDragLayer.setTranslationY(0f);
        mDragLayer.getScrim().hideSysUiScrim(false);
    }

    private boolean hasControlRemoteAppTransitionPermission() {
        return mLauncher.checkSelfPermission(CONTROL_REMOTE_APP_TRANSITION_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
