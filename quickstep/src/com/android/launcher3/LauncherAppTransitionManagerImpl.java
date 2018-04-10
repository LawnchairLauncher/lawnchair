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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE_IN_OUT;
import static com.android.launcher3.anim.Interpolators.APP_CLOSE_ALPHA;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.TaskUtils.findTaskViewToLaunch;
import static com.android.quickstep.TaskUtils.getRecentsWindowAnimator;
import static com.android.quickstep.TaskUtils.taskIsATargetWithMode;
import static com.android.systemui.shared.recents.utilities.Utilities.getNextFrameNumber;
import static com.android.systemui.shared.recents.utilities.Utilities.getSurface;
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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
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
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.ActivityCompat;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationDefinitionCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.TransactionCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * Manages the opening and closing app transitions from Launcher.
 */
@TargetApi(Build.VERSION_CODES.O)
@SuppressWarnings("unused")
public class LauncherAppTransitionManagerImpl extends LauncherAppTransitionManager
        implements OnDeviceProfileChangeListener {

    private static final String TAG = "LauncherTransition";
    public static final int STATUS_BAR_TRANSITION_DURATION = 120;

    private static final String CONTROL_REMOTE_APP_TRANSITION_PERMISSION =
            "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS";

    private static final int APP_LAUNCH_DURATION = 500;
    // Use a shorter duration for x or y translation to create a curve effect
    private static final int APP_LAUNCH_CURVED_DURATION = 233;
    public static final int RECENTS_LAUNCH_DURATION = 336;
    private static final int LAUNCHER_RESUME_START_DELAY = 100;
    private static final int CLOSING_TRANSITION_DURATION_MS = 350;

    // Progress = 0: All apps is fully pulled up, Progress = 1: All apps is fully pulled down.
    public static final float ALL_APPS_PROGRESS_OFF_SCREEN = 1.3059858f;
    public static final float ALL_APPS_PROGRESS_OVERSHOOT = 0.99581414f;

    private final DragLayer mDragLayer;
    private final Launcher mLauncher;

    private final Handler mHandler;
    private final boolean mIsRtl;

    private final float mContentTransY;
    private final float mWorkspaceTransY;

    private DeviceProfile mDeviceProfile;
    private View mFloatingView;

    private RemoteAnimationProvider mRemoteAnimationProvider;

    private final AnimatorListenerAdapter mReapplyStateListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mLauncher.getStateManager().reapplyState();
        }
    };

    public LauncherAppTransitionManagerImpl(Context context) {
        mLauncher = Launcher.getLauncher(context);
        mDragLayer = mLauncher.getDragLayer();
        mHandler = new Handler(Looper.getMainLooper());
        mIsRtl = Utilities.isRtl(mLauncher.getResources());
        mDeviceProfile = mLauncher.getDeviceProfile();


        Resources res = mLauncher.getResources();
        mContentTransY = res.getDimensionPixelSize(R.dimen.content_trans_y);
        mWorkspaceTransY = res.getDimensionPixelSize(R.dimen.workspace_trans_y);

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
            try {
                RemoteAnimationRunnerCompat runner = new LauncherAnimationRunner(mHandler) {

                    @Override
                    public AnimatorSet getAnimator(RemoteAnimationTargetCompat[] targetCompats) {
                        AnimatorSet anim = new AnimatorSet();


                        if (!composeRecentsLaunchAnimator(v, targetCompats, anim)) {
                            // Set the state animation first so that any state listeners are called
                            // before our internal listeners.
                            mLauncher.getStateManager().setCurrentAnimation(anim);

                            anim.play(getIconAnimator(v));
                            if (launcherIsATargetWithMode(targetCompats, MODE_CLOSING)) {
                                anim.play(getLauncherContentAnimator(false /* show */));
                            }
                            anim.play(getWindowAnimators(v, targetCompats));
                        }
                        return anim;
                    }
                };

                int duration = findTaskViewToLaunch(launcher, v, null) != null
                        ? RECENTS_LAUNCH_DURATION : APP_LAUNCH_DURATION;
                int statusBarTransitionDelay = duration - STATUS_BAR_TRANSITION_DURATION;
                return ActivityOptionsCompat.makeRemoteAnimation(new RemoteAnimationAdapterCompat(
                        runner, duration, statusBarTransitionDelay));
            } catch (NoClassDefFoundError e) {
                // Gracefully fall back to default launch options if the user's platform doesn't
                // have the latest changes.
            }
        }
        return getDefaultActivityLaunchOptions(launcher, v);
    }

    public void setRemoteAnimationProvider(RemoteAnimationProvider animationProvider) {
        mRemoteAnimationProvider = animationProvider;
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

        TaskView taskView = findTaskViewToLaunch(mLauncher, v, targets);
        if (taskView == null) {
            return false;
        }

        Animator childStateAnimation = null;
        // Found a visible recents task that matches the opening app, lets launch the app from there
        Animator launcherAnim;
        final AnimatorListenerAdapter windowAnimEndListener;
        if (launcherClosing) {
            launcherAnim = recentsView.createAdjacentPageAnimForTaskLaunch(taskView);
            launcherAnim.setInterpolator(Interpolators.TOUCH_RESPONSE_INTERPOLATOR);
            launcherAnim.setDuration(RECENTS_LAUNCH_DURATION);

            // Make sure recents gets fixed up by resetting task alphas and scales, etc.
            windowAnimEndListener = mReapplyStateListener;
        } else {
            AnimatorPlaybackController controller =
                    mLauncher.getStateManager()
                            .createAnimationToNewWorkspace(NORMAL, RECENTS_LAUNCH_DURATION);
            controller.dispatchOnStart();
            childStateAnimation = controller.getOriginalTarget();
            launcherAnim = controller.getAnimationPlayer().setDuration(RECENTS_LAUNCH_DURATION);
            windowAnimEndListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLauncher.getStateManager().goToState(NORMAL, false);
                }
            };
        }

        target.play(getRecentsWindowAnimator(taskView, skipLauncherChanges, targets)
                .setDuration(RECENTS_LAUNCH_DURATION));
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
     * @param show If true: Animate the content so that it moves upwards and fades in.
     *             Else: Animate the content so that it moves downwards and fades out.
     */
    private AnimatorSet getLauncherContentAnimator(boolean show) {
        AnimatorSet launcherAnimator = new AnimatorSet();

        float[] alphas = show
                ? new float[] {0, 1}
                : new float[] {1, 0};
        float[] trans = show
                ? new float[] {mContentTransY, 0,}
                : new float[] {0, mContentTransY};

        if (mLauncher.isInState(LauncherState.ALL_APPS) && !mDeviceProfile.isVerticalBarLayout()) {
            // All Apps in portrait mode is full screen, so we only animate AllAppsContainerView.
            final View appsView = mLauncher.getAppsView();
            final float startAlpha = appsView.getAlpha();
            final float startY = appsView.getTranslationY();
            appsView.setAlpha(alphas[0]);
            appsView.setTranslationY(trans[0]);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(appsView, View.ALPHA, alphas);
            alpha.setDuration(217);
            alpha.setInterpolator(LINEAR);
            ObjectAnimator transY = ObjectAnimator.ofFloat(appsView, View.TRANSLATION_Y, trans);
            transY.setInterpolator(AGGRESSIVE_EASE);
            transY.setDuration(350);

            launcherAnimator.play(alpha);
            launcherAnimator.play(transY);

            launcherAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    appsView.setAlpha(startAlpha);
                    appsView.setTranslationY(startY);
                }
            });
        } else {
            mDragLayer.setAlpha(alphas[0]);
            mDragLayer.setTranslationY(trans[0]);

            ObjectAnimator dragLayerAlpha = ObjectAnimator.ofFloat(mDragLayer, View.ALPHA, alphas);
            dragLayerAlpha.setDuration(217);
            dragLayerAlpha.setInterpolator(LINEAR);
            ObjectAnimator dragLayerTransY = ObjectAnimator.ofFloat(mDragLayer, View.TRANSLATION_Y,
                    trans);
            dragLayerTransY.setInterpolator(AGGRESSIVE_EASE);
            dragLayerTransY.setDuration(350);

            launcherAnimator.play(dragLayerAlpha);
            launcherAnimator.play(dragLayerTransY);
            launcherAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mDragLayer.setAlpha(1);
                    mDragLayer.setTranslationY(0);
                }
            });
        }
        return launcherAnimator;
    }

    /**
     * @return Animator that controls the icon used to launch the target.
     */
    private AnimatorSet getIconAnimator(View v) {
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
                ? mDeviceProfile.widthPx - rect.right
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

        AnimatorSet appIconAnimatorSet = new AnimatorSet();
        // Animate the app icon to the center
        float centerX = mDeviceProfile.widthPx / 2;
        float centerY = mDeviceProfile.heightPx / 2;

        float xPosition = mIsRtl
                ? mDeviceProfile.widthPx - lp.getMarginStart() - rect.width()
                : lp.getMarginStart();
        float dX = centerX - xPosition - (lp.width / 2);
        float dY = centerY - lp.topMargin - (lp.height / 2);

        ObjectAnimator x = ObjectAnimator.ofFloat(mFloatingView, View.TRANSLATION_X, 0f, dX);
        ObjectAnimator y = ObjectAnimator.ofFloat(mFloatingView, View.TRANSLATION_Y, 0f, dY);

        // Adjust the duration to change the "curve" of the app icon to the center.
        boolean isBelowCenterY = lp.topMargin < centerY;
        x.setDuration(isBelowCenterY ? APP_LAUNCH_DURATION : APP_LAUNCH_CURVED_DURATION);
        y.setDuration(isBelowCenterY ? APP_LAUNCH_CURVED_DURATION : APP_LAUNCH_DURATION);
        x.setInterpolator(AGGRESSIVE_EASE);
        y.setInterpolator(AGGRESSIVE_EASE);
        appIconAnimatorSet.play(x);
        appIconAnimatorSet.play(y);

        // Scale the app icon to take up the entire screen. This simplifies the math when
        // animating the app window position / scale.
        float maxScaleX = mDeviceProfile.widthPx / (float) rect.width();
        float maxScaleY = mDeviceProfile.heightPx / (float) rect.height();
        float scale = Math.max(maxScaleX, maxScaleY);
        ObjectAnimator scaleAnim = ObjectAnimator
                .ofFloat(mFloatingView, SCALE_PROPERTY, startScale, scale);
        scaleAnim.setDuration(APP_LAUNCH_DURATION).setInterpolator(Interpolators.EXAGGERATED_EASE);
        appIconAnimatorSet.play(scaleAnim);

        // Fade out the app icon.
        ObjectAnimator alpha = ObjectAnimator.ofFloat(mFloatingView, View.ALPHA, 1f, 0f);
        alpha.setStartDelay(32);
        alpha.setDuration(50);
        alpha.setInterpolator(LINEAR);
        appIconAnimatorSet.play(alpha);

        appIconAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Reset launcher to normal state
                v.setVisibility(View.VISIBLE);
                ((ViewGroup) mDragLayer.getParent()).removeView(mFloatingView);
            }
        });
        return appIconAnimatorSet;
    }

    /**
     * @return Animator that controls the window of the opening targets.
     */
    private ValueAnimator getWindowAnimators(View v, RemoteAnimationTargetCompat[] targets) {
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

        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setDuration(APP_LAUNCH_DURATION);
        appAnimator.addUpdateListener(new MultiValueUpdateListener() {
            // Fade alpha for the app window.
            FloatProp mAlpha = new FloatProp(0f, 1f, 0, 60, LINEAR);

            boolean isFirstFrame = true;

            @Override
            public void onUpdate(float percent) {
                final Surface surface = getSurface(mFloatingView);
                final long frameNumber = surface != null ? getNextFrameNumber(surface) : -1;
                if (frameNumber == -1) {
                    // Booo, not cool! Our surface got destroyed, so no reason to animate anything.
                    Log.w(TAG, "Failed to animate, surface got destroyed.");
                    return;
                }
                final float easePercent = AGGRESSIVE_EASE.getInterpolation(percent);

                // Calculate app icon size.
                float iconWidth = bounds.width() * mFloatingView.getScaleX();
                float iconHeight = bounds.height() * mFloatingView.getScaleY();

                // Scale the app window to match the icon size.
                float scaleX = iconWidth / mDeviceProfile.widthPx;
                float scaleY = iconHeight / mDeviceProfile.heightPx;
                float scale = Math.min(1f, Math.min(scaleX, scaleY));
                matrix.setScale(scale, scale);

                // Position the scaled window on top of the icon
                int deviceWidth = mDeviceProfile.widthPx;
                int deviceHeight = mDeviceProfile.heightPx;
                float scaledWindowWidth = deviceWidth * scale;
                float scaledWindowHeight = deviceHeight * scale;

                float offsetX = (scaledWindowWidth - iconWidth) / 2;
                float offsetY = (scaledWindowHeight - iconHeight) / 2;
                mFloatingView.getLocationInWindow(floatingViewBounds);
                float transX0 = floatingViewBounds[0] - offsetX;
                float transY0 = floatingViewBounds[1] - offsetY;
                matrix.postTranslate(transX0, transY0);

                // Animate the window crop so that it starts off as a square, and then reveals
                // horizontally.
                float cropHeight = deviceHeight * easePercent + deviceWidth * (1 - easePercent);
                float initialTop = (deviceHeight - deviceWidth) / 2f;
                crop.left = 0;
                crop.top = (int) (initialTop * (1 - easePercent));
                crop.right = deviceWidth;
                crop.bottom = (int) (crop.top + cropHeight);

                TransactionCompat t = new TransactionCompat();
                for (RemoteAnimationTargetCompat target : targets) {
                    if (target.mode == MODE_OPENING) {
                        t.setAlpha(target.leash, mAlpha.value);

                        // TODO: This isn't correct at the beginning of the animation, but better
                        // than nothing.
                        matrix.postTranslate(target.position.x, target.position.y);
                        t.setMatrix(target.leash, matrix);
                        t.setWindowCrop(target.leash, crop);
                        t.deferTransactionUntil(target.leash, surface, getNextFrameNumber(surface));
                    }
                    if (isFirstFrame) {
                        t.show(target.leash);
                    }
                }
                t.setEarlyWakeup();
                t.apply();

                matrix.reset();
                isFirstFrame = false;
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
            try {
                RemoteAnimationDefinitionCompat definition = new RemoteAnimationDefinitionCompat();
                definition.addRemoteAnimation(WindowManagerWrapper.TRANSIT_WALLPAPER_OPEN,
                        WindowManagerWrapper.ACTIVITY_TYPE_STANDARD,
                        new RemoteAnimationAdapterCompat(getWallpaperOpenRunner(),
                                CLOSING_TRANSITION_DURATION_MS, 0 /* statusBarTransitionDelay */));

//      TODO: App controlled transition for unlock to home TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER

                new ActivityCompat(mLauncher).registerRemoteAnimations(definition);
            } catch (NoClassDefFoundError e) {
                // Gracefully fall back if the user's platform doesn't have the latest changes
            }
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
        return new LauncherAnimationRunner(mHandler) {
            @Override
            public AnimatorSet getAnimator(RemoteAnimationTargetCompat[] targetCompats) {
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

                mLauncher.setForceInvisible(false);
                return anim;
            }
        };
    }

    /**
     * Animator that controls the transformations of the windows the targets that are closing.
     */
    private Animator getClosingWindowAnimators(RemoteAnimationTargetCompat[] targets) {
        Matrix matrix = new Matrix();
        float height = mLauncher.getDeviceProfile().heightPx;
        float width = mLauncher.getDeviceProfile().widthPx;
        float endX = (mLauncher.<RecentsView>getOverviewPanel().isRtl() ? -width : width) * 1.16f;

        ValueAnimator closingAnimator = ValueAnimator.ofFloat(0, 1);
        closingAnimator.setDuration(CLOSING_TRANSITION_DURATION_MS);
        closingAnimator.addUpdateListener(new MultiValueUpdateListener() {
            FloatProp mDx = new FloatProp(0, endX, 0, 350, AGGRESSIVE_EASE_IN_OUT);
            FloatProp mScale = new FloatProp(1f, 0.8f, 0, 267, AGGRESSIVE_EASE);
            FloatProp mAlpha = new FloatProp(1f, 0f, 0, 350, APP_CLOSE_ALPHA);

            boolean isFirstFrame = true;

            @Override
            public void onUpdate(float percent) {
                TransactionCompat t = new TransactionCompat();
                for (RemoteAnimationTargetCompat app : targets) {
                    if (app.mode == RemoteAnimationTargetCompat.MODE_CLOSING) {
                        t.setAlpha(app.leash, mAlpha.value);
                        matrix.setScale(mScale.value, mScale.value,
                                app.sourceContainerBounds.centerX(),
                                app.sourceContainerBounds.centerY());
                        matrix.postTranslate(mDx.value, 0);
                        matrix.postTranslate(app.position.x, app.position.y);
                        t.setMatrix(app.leash, matrix);
                    }
                    if (isFirstFrame) {
                        int layer = app.mode == RemoteAnimationTargetCompat.MODE_CLOSING
                                ? Integer.MAX_VALUE
                                : app.prefixOrderIndex;
                        t.setLayer(app.leash, layer);
                        t.show(app.leash);
                    }
                }
                t.setEarlyWakeup();
                t.apply();

                matrix.reset();
                isFirstFrame = false;
            }
        });

        return closingAnimator;
    }

    /**
     * Creates an animator that modifies Launcher as a result from {@link #getWallpaperOpenRunner}.
     */
    private void createLauncherResumeAnimation(AnimatorSet anim) {
        if (mLauncher.isInState(LauncherState.ALL_APPS)
                || mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            AnimatorSet contentAnimator = getLauncherContentAnimator(true /* show */);
            contentAnimator.setStartDelay(LAUNCHER_RESUME_START_DELAY);
            anim.play(contentAnimator);
        } else {
            AnimatorSet workspaceAnimator = new AnimatorSet();

            mLauncher.getWorkspace().setTranslationY(mWorkspaceTransY);
            workspaceAnimator.play(ObjectAnimator.ofFloat(mLauncher.getWorkspace(),
                    View.TRANSLATION_Y, mWorkspaceTransY, 0));

            View currentPage = ((CellLayout) mLauncher.getWorkspace()
                    .getChildAt(mLauncher.getWorkspace().getCurrentPage()))
                    .getShortcutsAndWidgets();
            currentPage.setAlpha(0f);
            workspaceAnimator.play(ObjectAnimator.ofFloat(currentPage, View.ALPHA, 0, 1f));

            workspaceAnimator.setStartDelay(LAUNCHER_RESUME_START_DELAY);
            workspaceAnimator.setDuration(333);
            workspaceAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);

            // Animate the shelf in two parts: slide in, and overeshoot.
            AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
            // The shelf will start offscreen
            final float startY = ALL_APPS_PROGRESS_OFF_SCREEN;
            // And will end slightly pulled up, so that there is something to overshoot back to 1f.
            final float slideEnd = ALL_APPS_PROGRESS_OVERSHOOT;

            allAppsController.setProgress(startY);

            Animator allAppsSlideIn =
                    ObjectAnimator.ofFloat(allAppsController, ALL_APPS_PROGRESS, startY, slideEnd);
            allAppsSlideIn.setStartDelay(LAUNCHER_RESUME_START_DELAY);
            allAppsSlideIn.setDuration(317);
            allAppsSlideIn.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);

            Animator allAppsOvershoot =
                    ObjectAnimator.ofFloat(allAppsController, ALL_APPS_PROGRESS, slideEnd, 1f);
            allAppsOvershoot.setDuration(153);
            allAppsOvershoot.setInterpolator(Interpolators.OVERSHOOT_0);


            anim.play(workspaceAnimator);
            anim.playSequentially(allAppsSlideIn, allAppsOvershoot);
            anim.addListener(mReapplyStateListener);
        }
    }

    private boolean hasControlRemoteAppTransitionPermission() {
        return mLauncher.checkSelfPermission(CONTROL_REMOTE_APP_TRANSITION_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }
}
