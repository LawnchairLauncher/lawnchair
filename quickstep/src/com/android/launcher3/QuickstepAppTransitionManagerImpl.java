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
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE;
import static com.android.launcher3.anim.Interpolators.DEACCEL_1_7;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.dragndrop.DragLayer.ALPHA_INDEX_TRANSITIONS;
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
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.systemui.shared.system.ActivityCompat;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationDefinitionCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;
import com.android.systemui.shared.system.WindowManagerWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * {@link LauncherAppTransitionManager} with Quickstep-specific app transitions for launching from
 * home and/or all-apps.
 */
@TargetApi(Build.VERSION_CODES.O)
@SuppressWarnings("unused")
public abstract class QuickstepAppTransitionManagerImpl extends LauncherAppTransitionManager
        implements OnDeviceProfileChangeListener {

    private static final String TAG = "QuickstepTransition";

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
    private static final int LAUNCHER_RESUME_START_DELAY = 100;
    private static final int CLOSING_TRANSITION_DURATION_MS = 250;

    protected static final int CONTENT_ALPHA_DURATION = 217;
    protected static final int CONTENT_TRANSLATION_DURATION = 350;

    // Progress = 0: All apps is fully pulled up, Progress = 1: All apps is fully pulled down.
    public static final float ALL_APPS_PROGRESS_OFF_SCREEN = 1.3059858f;

    protected final Launcher mLauncher;

    private final DragLayer mDragLayer;
    private final AlphaProperty mDragLayerAlpha;

    private final Handler mHandler;
    private final boolean mIsRtl;

    private final float mContentTransY;
    private final float mWorkspaceTransY;
    private final float mClosingWindowTransY;

    private DeviceProfile mDeviceProfile;
    private FloatingIconView mFloatingView;

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

    public QuickstepAppTransitionManagerImpl(Context context) {
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
            boolean fromRecents = isLaunchingFromRecents(v, null /* targets */);
            RemoteAnimationRunnerCompat runner = new LauncherAnimationRunner(mHandler,
                    true /* startAtFrontOfQueue */) {

                @Override
                public void onCreateAnimation(RemoteAnimationTargetCompat[] targetCompats,
                        AnimationResult result) {
                    AnimatorSet anim = new AnimatorSet();

                    boolean launcherClosing =
                            launcherIsATargetWithMode(targetCompats, MODE_CLOSING);

                    if (isLaunchingFromRecents(v, targetCompats)) {
                        composeRecentsLaunchAnimator(anim, v, targetCompats, launcherClosing);
                    } else {
                        composeIconLaunchAnimator(anim, v, targetCompats, launcherClosing);
                    }

                    if (launcherClosing) {
                        anim.addListener(mForceInvisibleListener);
                    }

                    result.setAnimation(anim);
                }
            };

            // Note that this duration is a guess as we do not know if the animation will be a
            // recents launch or not for sure until we know the opening app targets.
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
     * Whether the launch is a recents app transition and we should do a launch animation
     * from the recents view. Note that if the remote animation targets are not provided, this
     * may not always be correct as we may resolve the opening app to a task when the animation
     * starts.
     *
     * @param v the view to launch from
     * @param targets apps that are opening/closing
     * @return true if the app is launching from recents, false if it most likely is not
     */
    protected abstract boolean isLaunchingFromRecents(@NonNull View v,
            @Nullable RemoteAnimationTargetCompat[] targets);

    /**
     * Composes the animations for a launch from the recents list.
     *
     * @param anim the animator set to add to
     * @param v the launching view
     * @param targets the apps that are opening/closing
     * @param launcherClosing true if the launcher app is closing
     */
    protected abstract void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTargetCompat[] targets, boolean launcherClosing);

    /**
     * Compose the animations for a launch from the app icon.
     *
     * @param anim the animation to add to
     * @param v the launching view with the icon
     * @param targets the list of opening/closing apps
     * @param launcherClosing true if launcher is closing
     */
    private void composeIconLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTargetCompat[] targets, boolean launcherClosing) {
        // Set the state animation first so that any state listeners are called
        // before our internal listeners.
        mLauncher.getStateManager().setCurrentAnimation(anim);

        Rect windowTargetBounds = getWindowTargetBounds(targets);
        boolean isAllOpeningTargetTrs = true;
        for (int i = 0; i < targets.length; i++) {
            RemoteAnimationTargetCompat target = targets[i];
            if (target.mode == MODE_OPENING) {
                isAllOpeningTargetTrs &= target.isTranslucent;
            }
            if (!isAllOpeningTargetTrs) break;
        }
        playIconAnimators(anim, v, windowTargetBounds, !isAllOpeningTargetTrs);
        if (launcherClosing) {
            Pair<AnimatorSet, Runnable> launcherContentAnimator =
                    getLauncherContentAnimator(true /* isAppOpening */,
                            new float[] {0, mContentTransY});
            anim.play(launcherContentAnimator.first);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    launcherContentAnimator.second.run();
                }
            });
        }
        anim.play(getOpeningWindowAnimators(v, targets, windowTargetBounds));
    }

    /**
     * Return the window bounds of the opening target.
     * In multiwindow mode, we need to get the final size of the opening app window target to help
     * figure out where the floating view should animate to.
     */
    private Rect getWindowTargetBounds(RemoteAnimationTargetCompat[] targets) {
        Rect bounds = new Rect(0, 0, mDeviceProfile.widthPx, mDeviceProfile.heightPx);
        if (mLauncher.isInMultiWindowMode()) {
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
     * Content is everything on screen except the background and the floating view (if any).
     *
     * @param isAppOpening True when this is called when an app is opening.
     *                     False when this is called when an app is closing.
     * @param trans Array that contains the start and end translation values for the content.
     */
    private Pair<AnimatorSet, Runnable> getLauncherContentAnimator(boolean isAppOpening,
            float[] trans) {
        AnimatorSet launcherAnimator = new AnimatorSet();
        Runnable endListener;

        float[] alphas = isAppOpening
                ? new float[] {1, 0}
                : new float[] {0, 1};

        if (mLauncher.isInState(ALL_APPS)) {
            // All Apps in portrait mode is full screen, so we only animate AllAppsContainerView.
            final View appsView = mLauncher.getAppsView();
            final float startAlpha = appsView.getAlpha();
            final float startY = appsView.getTranslationY();
            appsView.setAlpha(alphas[0]);
            appsView.setTranslationY(trans[0]);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(appsView, View.ALPHA, alphas);
            alpha.setDuration(CONTENT_ALPHA_DURATION);
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
            transY.setDuration(CONTENT_TRANSLATION_DURATION);

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
            endListener = composeViewContentAnimator(launcherAnimator, alphas, trans);
        } else {
            mDragLayerAlpha.setValue(alphas[0]);
            ObjectAnimator alpha =
                    ObjectAnimator.ofFloat(mDragLayerAlpha, MultiValueAlpha.VALUE, alphas);
            alpha.setDuration(CONTENT_ALPHA_DURATION);
            alpha.setInterpolator(LINEAR);
            launcherAnimator.play(alpha);

            mDragLayer.setTranslationY(trans[0]);
            ObjectAnimator transY = ObjectAnimator.ofFloat(mDragLayer, View.TRANSLATION_Y, trans);
            transY.setInterpolator(AGGRESSIVE_EASE);
            transY.setDuration(CONTENT_TRANSLATION_DURATION);
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
     * Compose recents view alpha and translation Y animation when launcher opens/closes apps.
     *
     * @param anim the animator set to add to
     * @param alphas the alphas to animate to over time
     * @param trans the translation Y values to animator to over time
     * @return listener to run when the animation ends
     */
    protected abstract Runnable composeViewContentAnimator(@NonNull AnimatorSet anim,
            float[] alphas, float[] trans);

    /**
     * Animators for the "floating view" of the view used to launch the target.
     */
    private void playIconAnimators(AnimatorSet appOpenAnimator, View v, Rect windowTargetBounds,
            boolean toggleVisibility) {
        final boolean isBubbleTextView = v instanceof BubbleTextView;
        if (mFloatingView != null) {
            mFloatingView.setTranslationX(0);
            mFloatingView.setTranslationY(0);
            mFloatingView.setScaleX(1);
            mFloatingView.setScaleY(1);
            mFloatingView.setAlpha(1);
            mFloatingView.setBackground(null);
        }
        Rect rect = new Rect();
        mFloatingView = FloatingIconView.getFloatingIconView(mLauncher, v, toggleVisibility,
                true /* useDrawableAsIs */, -1 /* aspectRatio */, rect, mFloatingView);

        int viewLocationStart = mIsRtl ? windowTargetBounds.width() - rect.right : rect.left;
        LayoutParams lp = (LayoutParams) mFloatingView.getLayoutParams();
        // Special RTL logic is needed to handle the window target bounds.
        lp.leftMargin = mIsRtl ? windowTargetBounds.width() - rect.right : rect.left;
        mFloatingView.setLayoutParams(lp);

        int[] dragLayerBounds = new int[2];
        mDragLayer.getLocationOnScreen(dragLayerBounds);

        // Animate the app icon to the center of the window bounds in screen coordinates.
        float centerX = windowTargetBounds.centerX() - dragLayerBounds[0];
        float centerY = windowTargetBounds.centerY() - dragLayerBounds[1];

        float xPosition = mIsRtl
                ? windowTargetBounds.width() - lp.getMarginStart() - rect.width()
                : lp.getMarginStart();
        float dX = centerX - xPosition - (lp.width / 2f);
        float dY = centerY - lp.topMargin - (lp.height / 2f);

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
        float startScale = 1f;
        if (isBubbleTextView && !(v.getParent() instanceof DeepShortcutView)) {
            Drawable dr = ((BubbleTextView) v).getIcon();
            if (dr instanceof FastBitmapDrawable) {
                startScale = ((FastBitmapDrawable) dr).getAnimatedScale();
            }
        }

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

        appOpenAnimator.addListener(mFloatingView);
        appOpenAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Reset launcher to normal state
                if (isBubbleTextView) {
                    ((BubbleTextView) v).setStayPressed(false);
                }
                v.setVisibility(View.VISIBLE);
                ((ViewGroup) mDragLayer.getParent()).getOverlay().remove(mFloatingView);
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
        SyncRtSurfaceTransactionApplierCompat surfaceApplier =
                new SyncRtSurfaceTransactionApplierCompat(mFloatingView);

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

                float windowRadius = 0;
                if (!mDeviceProfile.isMultiWindowMode &&
                        RecentsModel.INSTANCE.get(mLauncher).supportsRoundedCornersOnWindows()) {
                    windowRadius = RecentsModel.INSTANCE.get(mLauncher)
                            .getWindowCornerRadius();
                }

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
                    final float alpha;
                    final float cornerRadius;
                    if (target.mode == MODE_OPENING) {
                        matrix.setScale(scale, scale);
                        matrix.postTranslate(transX0, transY0);
                        targetCrop = crop;
                        alpha = mAlpha.value;
                        cornerRadius = windowRadius;
                    } else {
                        matrix.setTranslate(target.position.x, target.position.y);
                        alpha = 1f;
                        targetCrop = target.sourceContainerBounds;
                        cornerRadius = 0;
                    }

                    params[i] = new SurfaceParams(target.leash, alpha, matrix, targetCrop,
                            RemoteAnimationProvider.getLayer(target, MODE_OPENING),
                            cornerRadius);
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
                    new RemoteAnimationAdapterCompat(getWallpaperOpenRunner(false /* fromUnlock */),
                            CLOSING_TRANSITION_DURATION_MS, 0 /* statusBarTransitionDelay */));
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
    private RemoteAnimationRunnerCompat getWallpaperOpenRunner(boolean fromUnlock) {
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
                    anim.play(fromUnlock
                            ? getUnlockWindowAnimator(targetCompats)
                            : getClosingWindowAnimators(targetCompats));

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
                        if (fromUnlock) {
                            Pair<AnimatorSet, Runnable> contentAnimator =
                                    getLauncherContentAnimator(false /* isAppOpening */,
                                            new float[] {mContentTransY, 0});
                            contentAnimator.first.setStartDelay(0);
                            anim.play(contentAnimator.first);
                            anim.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    contentAnimator.second.run();
                                }
                            });
                        } else {
                            createLauncherResumeAnimation(anim);
                        }
                    }
                }

                mLauncher.clearForceInvisibleFlag(INVISIBLE_ALL);
                result.setAnimation(anim);
            }
        };
    }

    /**
     * Animator that controls the transformations of the windows when unlocking the device.
     */
    private Animator getUnlockWindowAnimator(RemoteAnimationTargetCompat[] targets) {
        SyncRtSurfaceTransactionApplierCompat surfaceApplier =
                new SyncRtSurfaceTransactionApplierCompat(mDragLayer);
        ValueAnimator unlockAnimator = ValueAnimator.ofFloat(0, 1);
        unlockAnimator.setDuration(CLOSING_TRANSITION_DURATION_MS);
        float cornerRadius = mDeviceProfile.isMultiWindowMode ? 0 :
                RecentsModel.INSTANCE.get(mLauncher).getWindowCornerRadius();
        unlockAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                SurfaceParams[] params = new SurfaceParams[targets.length];
                for (int i = targets.length - 1; i >= 0; i--) {
                    RemoteAnimationTargetCompat target = targets[i];
                    params[i] = new SurfaceParams(target.leash, 1f, null,
                            target.sourceContainerBounds,
                            RemoteAnimationProvider.getLayer(target, MODE_OPENING), cornerRadius);
                }
                surfaceApplier.scheduleApply(params);
            }
        });
        return unlockAnimator;
    }

    /**
     * Animator that controls the transformations of the windows the targets that are closing.
     */
    private Animator getClosingWindowAnimators(RemoteAnimationTargetCompat[] targets) {
        SyncRtSurfaceTransactionApplierCompat surfaceApplier =
                new SyncRtSurfaceTransactionApplierCompat(mDragLayer);
        Matrix matrix = new Matrix();
        ValueAnimator closingAnimator = ValueAnimator.ofFloat(0, 1);
        int duration = CLOSING_TRANSITION_DURATION_MS;
        float windowCornerRadius = mDeviceProfile.isMultiWindowMode ? 0 :
                RecentsModel.INSTANCE.get(mLauncher).getWindowCornerRadius();
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
                    final float alpha;
                    final float cornerRadius;
                    if (target.mode == MODE_CLOSING) {
                        matrix.setScale(mScale.value, mScale.value,
                                target.sourceContainerBounds.centerX(),
                                target.sourceContainerBounds.centerY());
                        matrix.postTranslate(0, mDy.value);
                        matrix.postTranslate(target.position.x, target.position.y);
                        alpha = mAlpha.value;
                        cornerRadius = windowCornerRadius;
                    } else {
                        matrix.setTranslate(target.position.x, target.position.y);
                        alpha = 1f;
                        cornerRadius = 0f;
                    }
                    params[i] = new SurfaceParams(target.leash, alpha, matrix,
                            target.sourceContainerBounds,
                            RemoteAnimationProvider.getLayer(target, MODE_CLOSING),
                            cornerRadius);
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
                    getLauncherContentAnimator(false /* isAppOpening */,
                            new float[] {-mContentTransY, 0});
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
