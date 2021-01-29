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
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.postAsyncCallback;
import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE;
import static com.android.launcher3.anim.Interpolators.DEACCEL_1_7;
import static com.android.launcher3.anim.Interpolators.EXAGGERATED_EASE;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.config.FeatureFlags.KEYGUARD_ANIMATION;
import static com.android.launcher3.config.FeatureFlags.SEPARATE_RECENTS_ACTIVITY;
import static com.android.launcher3.dragndrop.DragLayer.ALPHA_INDEX_TRANSITIONS;
import static com.android.launcher3.statehandlers.DepthController.DEPTH;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.quickstep.TaskUtils.taskIsATargetWithMode;
import static com.android.systemui.shared.system.QuickStepContract.getWindowCornerRadius;
import static com.android.systemui.shared.system.QuickStepContract.supportsRoundedCornersOnWindows;
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
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.util.DynamicResource;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.StaggeredWorkspaceAnim;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.systemui.shared.system.ActivityCompat;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationDefinitionCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * {@link LauncherAppTransitionManager} with Quickstep-specific app transitions for launching from
 * home and/or all-apps.  Not used for 3p launchers.
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

    private static final long APP_LAUNCH_DURATION = 450;
    // Use a shorter duration for x or y translation to create a curve effect
    private static final long APP_LAUNCH_CURVED_DURATION = 250;
    private static final long APP_LAUNCH_ALPHA_DURATION = 50;
    private static final long APP_LAUNCH_ALPHA_START_DELAY = 25;

    // We scale the durations for the downward app launch animations (minus the scale animation).
    private static final float APP_LAUNCH_DOWN_DUR_SCALE_FACTOR = 0.8f;
    private static final long APP_LAUNCH_DOWN_DURATION =
            (long) (APP_LAUNCH_DURATION * APP_LAUNCH_DOWN_DUR_SCALE_FACTOR);
    private static final long APP_LAUNCH_DOWN_CURVED_DURATION =
            (long) (APP_LAUNCH_CURVED_DURATION * APP_LAUNCH_DOWN_DUR_SCALE_FACTOR);
    private static final long APP_LAUNCH_ALPHA_DOWN_DURATION =
            (long) (APP_LAUNCH_ALPHA_DURATION * APP_LAUNCH_DOWN_DUR_SCALE_FACTOR);

    private static final long CROP_DURATION = 375;
    private static final long RADIUS_DURATION = 375;

    public static final int RECENTS_LAUNCH_DURATION = 336;
    private static final int LAUNCHER_RESUME_START_DELAY = 100;
    private static final int CLOSING_TRANSITION_DURATION_MS = 250;

    protected static final int CONTENT_ALPHA_DURATION = 217;
    protected static final int CONTENT_TRANSLATION_DURATION = 350;

    // Progress = 0: All apps is fully pulled up, Progress = 1: All apps is fully pulled down.
    public static final float ALL_APPS_PROGRESS_OFF_SCREEN = 1.3059858f;

    protected final BaseQuickstepLauncher mLauncher;

    private final DragLayer mDragLayer;
    private final AlphaProperty mDragLayerAlpha;

    final Handler mHandler;
    private final boolean mIsRtl;

    private final float mContentTransY;
    private final float mWorkspaceTransY;
    private final float mClosingWindowTransY;

    private DeviceProfile mDeviceProfile;

    private RemoteAnimationProvider mRemoteAnimationProvider;
    // Strong refs to runners which are cleared when the launcher activity is destroyed
    private WrappedAnimationRunnerImpl mWallpaperOpenRunner;
    private WrappedAnimationRunnerImpl mAppLaunchRunner;
    private WrappedAnimationRunnerImpl mKeyguardGoingAwayRunner;

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
        mLauncher = Launcher.cast(Launcher.getLauncher(context));
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
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mDeviceProfile = dp;
    }

    @Override
    public boolean supportsAdaptiveIconAnimation() {
        return hasControlRemoteAppTransitionPermission()
                && FeatureFlags.ADAPTIVE_ICON_WINDOW_ANIM.get();
    }

    /**
     * @return ActivityOptions with remote animations that controls how the window of the opening
     *         targets are displayed.
     */
    @Override
    public ActivityOptions getActivityLaunchOptions(Launcher launcher, View v) {
        if (hasControlRemoteAppTransitionPermission()) {
            boolean fromRecents = isLaunchingFromRecents(v, null /* targets */);
            mAppLaunchRunner = new AppLaunchAnimationRunner(mHandler, v);
            RemoteAnimationRunnerCompat runner = new WrappedLauncherAnimationRunner<>(
                    mAppLaunchRunner, true /* startAtFrontOfQueue */);

            // Note that this duration is a guess as we do not know if the animation will be a
            // recents launch or not for sure until we know the opening app targets.
            long duration = fromRecents
                    ? RECENTS_LAUNCH_DURATION
                    : APP_LAUNCH_DURATION;

            long statusBarTransitionDelay = duration - STATUS_BAR_TRANSITION_DURATION
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
     * @param appTargets the apps that are opening/closing
     * @param launcherClosing true if the launcher app is closing
     */
    protected abstract void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTargetCompat[] appTargets,
            @NonNull RemoteAnimationTargetCompat[] wallpaperTargets, boolean launcherClosing);

    /**
     * Compose the animations for a launch from the app icon.
     *
     * @param anim the animation to add to
     * @param v the launching view with the icon
     * @param appTargets the list of opening/closing apps
     * @param launcherClosing true if launcher is closing
     */
    private void composeIconLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTargetCompat[] appTargets,
            @NonNull RemoteAnimationTargetCompat[] wallpaperTargets,
            boolean launcherClosing) {
        // Set the state animation first so that any state listeners are called
        // before our internal listeners.
        mLauncher.getStateManager().setCurrentAnimation(anim);

        Rect windowTargetBounds = getWindowTargetBounds(appTargets);
        boolean isAllOpeningTargetTrs = true;
        for (int i = 0; i < appTargets.length; i++) {
            RemoteAnimationTargetCompat target = appTargets[i];
            if (target.mode == MODE_OPENING) {
                isAllOpeningTargetTrs &= target.isTranslucent;
            }
            if (!isAllOpeningTargetTrs) break;
        }
        anim.play(getOpeningWindowAnimators(v, appTargets, wallpaperTargets, windowTargetBounds,
                !isAllOpeningTargetTrs));
        if (launcherClosing) {
            Pair<AnimatorSet, Runnable> launcherContentAnimator =
                    getLauncherContentAnimator(true /* isAppOpening */,
                            new float[] {0, -mContentTransY});
            anim.play(launcherContentAnimator.first);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    launcherContentAnimator.second.run();
                }
            });
        } else {
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    mLauncher.addOnResumeCallback(() ->
                            ObjectAnimator.ofFloat(mLauncher.getDepthController(), DEPTH,
                            mLauncher.getStateManager().getState().getDepth(mLauncher)).start());
                }
            });
        }
    }

    /**
     * Return the window bounds of the opening target.
     * In multiwindow mode, we need to get the final size of the opening app window target to help
     * figure out where the floating view should animate to.
     */
    private Rect getWindowTargetBounds(RemoteAnimationTargetCompat[] appTargets) {
        Rect bounds = new Rect(0, 0, mDeviceProfile.widthPx, mDeviceProfile.heightPx);
        if (mLauncher.isInMultiWindowMode()) {
            for (RemoteAnimationTargetCompat target : appTargets) {
                if (target.mode == MODE_OPENING) {
                    bounds.set(target.screenSpaceBounds);
                    if (target.localBounds != null) {
                        bounds.set(target.localBounds);
                    } else {
                        bounds.offsetTo(target.position.x, target.position.y);
                    }
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

            Workspace workspace = mLauncher.getWorkspace();
            View currentPage = ((CellLayout) workspace.getChildAt(workspace.getCurrentPage()))
                    .getShortcutsAndWidgets();
            View hotseat = mLauncher.getHotseat();
            View qsb = mLauncher.findViewById(R.id.search_container_all_apps);

            currentPage.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            hotseat.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            qsb.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            launcherAnimator.play(ObjectAnimator.ofFloat(currentPage, View.TRANSLATION_Y, trans));
            launcherAnimator.play(ObjectAnimator.ofFloat(hotseat, View.TRANSLATION_Y, trans));
            launcherAnimator.play(ObjectAnimator.ofFloat(qsb, View.TRANSLATION_Y, trans));

            // Pause page indicator animations as they lead to layer trashing.
            mLauncher.getWorkspace().getPageIndicator().pauseAnimations();

            endListener = () -> {
                currentPage.setTranslationY(0);
                hotseat.setTranslationY(0);
                qsb.setTranslationY(0);

                currentPage.setLayerType(View.LAYER_TYPE_NONE, null);
                hotseat.setLayerType(View.LAYER_TYPE_NONE, null);
                qsb.setLayerType(View.LAYER_TYPE_NONE, null);

                mDragLayerAlpha.setValue(1f);
                mLauncher.getWorkspace().getPageIndicator().skipAnimationsToEnd();
            };
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
     * @return Animator that controls the window of the opening targets from app icons.
     */
    private Animator getOpeningWindowAnimators(View v,
            RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets,
            Rect windowTargetBounds, boolean toggleVisibility) {
        RectF bounds = new RectF();
        FloatingIconView floatingView = FloatingIconView.getFloatingIconView(mLauncher, v,
                toggleVisibility, bounds, true /* isOpening */);
        Rect crop = new Rect();
        Matrix matrix = new Matrix();

        RemoteAnimationTargets openingTargets = new RemoteAnimationTargets(appTargets,
                wallpaperTargets, MODE_OPENING);
        SurfaceTransactionApplier surfaceApplier =
                new SurfaceTransactionApplier(floatingView);
        openingTargets.addReleaseCheck(surfaceApplier);

        // Scale the app icon to take up the entire screen. This simplifies the math when
        // animating the app window position / scale.
        float smallestSize = Math.min(windowTargetBounds.height(), windowTargetBounds.width());
        float maxScaleX = smallestSize / bounds.width();
        float maxScaleY = smallestSize / bounds.height();
        float scale = Math.max(maxScaleX, maxScaleY);
        float startScale = 1f;
        if (v instanceof BubbleTextView && !(v.getParent() instanceof DeepShortcutView)) {
            Drawable dr = ((BubbleTextView) v).getIcon();
            if (dr instanceof FastBitmapDrawable) {
                startScale = ((FastBitmapDrawable) dr).getAnimatedScale();
            }
        }
        final float initialStartScale = startScale;

        int[] dragLayerBounds = new int[2];
        mDragLayer.getLocationOnScreen(dragLayerBounds);

        // Animate the app icon to the center of the window bounds in screen coordinates.
        float centerX = windowTargetBounds.centerX() - dragLayerBounds[0];
        float centerY = windowTargetBounds.centerY() - dragLayerBounds[1];

        float dX = centerX - bounds.centerX();
        float dY = centerY - bounds.centerY();

        boolean useUpwardAnimation = bounds.top > centerY
                || Math.abs(dY) < mLauncher.getDeviceProfile().cellHeightPx;
        final long xDuration = useUpwardAnimation ? APP_LAUNCH_CURVED_DURATION
                : APP_LAUNCH_DOWN_DURATION;
        final long yDuration = useUpwardAnimation ? APP_LAUNCH_DURATION
                : APP_LAUNCH_DOWN_CURVED_DURATION;
        final long alphaDuration = useUpwardAnimation ? APP_LAUNCH_ALPHA_DURATION
                : APP_LAUNCH_ALPHA_DOWN_DURATION;

        RectF targetBounds = new RectF(windowTargetBounds);
        RectF iconBounds = new RectF();
        RectF temp = new RectF();
        Point tmpPos = new Point();

        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setDuration(APP_LAUNCH_DURATION);
        appAnimator.setInterpolator(LINEAR);
        appAnimator.addListener(floatingView);
        appAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (v instanceof BubbleTextView) {
                    ((BubbleTextView) v).setStayPressed(false);
                }
                openingTargets.release();
            }
        });

        float shapeRevealDuration = APP_LAUNCH_DURATION * SHAPE_PROGRESS_DURATION;

        final float startCrop;
        final float endCrop;
        if (mDeviceProfile.isVerticalBarLayout()) {
            startCrop = windowTargetBounds.height();
            endCrop = windowTargetBounds.width();
        } else {
            startCrop = windowTargetBounds.width();
            endCrop = windowTargetBounds.height();
        }

        final float initialWindowRadius = supportsRoundedCornersOnWindows(mLauncher.getResources())
                ? startCrop / 2f : 0f;
        final float windowRadius = mDeviceProfile.isMultiWindowMode
                ? 0 : getWindowCornerRadius(mLauncher.getResources());
        appAnimator.addUpdateListener(new MultiValueUpdateListener() {
            FloatProp mDx = new FloatProp(0, dX, 0, xDuration, AGGRESSIVE_EASE);
            FloatProp mDy = new FloatProp(0, dY, 0, yDuration, AGGRESSIVE_EASE);
            FloatProp mScale = new FloatProp(initialStartScale, scale, 0, APP_LAUNCH_DURATION,
                    EXAGGERATED_EASE);
            FloatProp mIconAlpha = new FloatProp(1f, 0f, APP_LAUNCH_ALPHA_START_DELAY,
                    alphaDuration, LINEAR);
            FloatProp mCroppedSize = new FloatProp(startCrop, endCrop, 0, CROP_DURATION,
                    EXAGGERATED_EASE);
            FloatProp mWindowRadius = new FloatProp(initialWindowRadius, windowRadius, 0,
                    RADIUS_DURATION, EXAGGERATED_EASE);

            @Override
            public void onUpdate(float percent) {
                // Calculate the size.
                float width = bounds.width() * mScale.value;
                float height = bounds.height() * mScale.value;

                // Animate the crop so that it starts off as a square.
                final int cropWidth;
                final int cropHeight;
                if (mDeviceProfile.isVerticalBarLayout()) {
                    cropWidth = (int) mCroppedSize.value;
                    cropHeight = windowTargetBounds.height();
                } else {
                    cropWidth = windowTargetBounds.width();
                    cropHeight = (int) mCroppedSize.value;
                }
                crop.set(0, 0, cropWidth, cropHeight);

                // Scale the size to match the crop.
                float scaleX = width / cropWidth;
                float scaleY = height / cropHeight;
                float scale = Math.min(1f, Math.max(scaleX, scaleY));

                float scaledCropWidth = cropWidth * scale;
                float scaledCropHeight = cropHeight * scale;
                float offsetX  = (scaledCropWidth - width) / 2;
                float offsetY = (scaledCropHeight - height) / 2;

                // Calculate the window position.
                temp.set(bounds);
                temp.offset(dragLayerBounds[0], dragLayerBounds[1]);
                temp.offset(mDx.value, mDy.value);
                Utilities.scaleRectFAboutCenter(temp, mScale.value);
                float windowTransX0 = temp.left - offsetX;
                float windowTransY0 = temp.top - offsetY;

                // Calculate the icon position.
                iconBounds.set(bounds);
                iconBounds.offset(mDx.value, mDy.value);
                Utilities.scaleRectFAboutCenter(iconBounds, mScale.value);
                iconBounds.left -= offsetX;
                iconBounds.top -= offsetY;
                iconBounds.right += offsetX;
                iconBounds.bottom += offsetY;

                float croppedHeight = (windowTargetBounds.height() - crop.height()) * scale;
                float croppedWidth = (windowTargetBounds.width() - crop.width()) * scale;
                SurfaceParams[] params = new SurfaceParams[appTargets.length];
                for (int i = appTargets.length - 1; i >= 0; i--) {
                    RemoteAnimationTargetCompat target = appTargets[i];
                    SurfaceParams.Builder builder = new SurfaceParams.Builder(target.leash);

                    if (target.mode == MODE_OPENING) {
                        matrix.setScale(scale, scale);
                        matrix.postTranslate(windowTransX0, windowTransY0);

                        floatingView.update(iconBounds, mIconAlpha.value, percent, 0f,
                                mWindowRadius.value * scale, true /* isOpening */);
                        builder.withMatrix(matrix)
                                .withWindowCrop(crop)
                                .withAlpha(1f - mIconAlpha.value)
                                .withCornerRadius(mWindowRadius.value);
                    } else {
                        tmpPos.set(target.position.x, target.position.y);
                        if (target.localBounds != null) {
                            final Rect localBounds = target.localBounds;
                            tmpPos.set(target.localBounds.left, target.localBounds.top);
                        }

                        matrix.setTranslate(tmpPos.x, tmpPos.y);
                        final Rect crop = new Rect(target.screenSpaceBounds);
                        crop.offsetTo(0, 0);
                        builder.withMatrix(matrix)
                                .withWindowCrop(crop)
                                .withAlpha(1f);
                    }
                    params[i] = builder.build();
                }
                surfaceApplier.scheduleApply(params);
            }
        });

        // When launching an app from overview that doesn't map to a task, we still want to just
        // blur the wallpaper instead of the launcher surface as well
        boolean allowBlurringLauncher = mLauncher.getStateManager().getState() != OVERVIEW;
        DepthController depthController = mLauncher.getDepthController();
        ObjectAnimator backgroundRadiusAnim = ObjectAnimator.ofFloat(depthController, DEPTH,
                BACKGROUND_APP.getDepth(mLauncher))
                .setDuration(APP_LAUNCH_DURATION);
        if (allowBlurringLauncher) {
            depthController.setSurfaceToApp(RemoteAnimationProvider.findLowestOpaqueLayerTarget(
                    appTargets, MODE_OPENING));
            backgroundRadiusAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    depthController.setSurfaceToApp(null);
                }
            });
        }

        animatorSet.playTogether(appAnimator, backgroundRadiusAnim);
        return animatorSet;
    }

    /**
     * Registers remote animations used when closing apps to home screen.
     */
    @Override
    public void registerRemoteAnimations() {
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        if (hasControlRemoteAppTransitionPermission()) {
            mWallpaperOpenRunner = createWallpaperOpenRunner(false /* fromUnlock */);

            RemoteAnimationDefinitionCompat definition = new RemoteAnimationDefinitionCompat();
            definition.addRemoteAnimation(WindowManagerWrapper.TRANSIT_WALLPAPER_OPEN,
                    WindowManagerWrapper.ACTIVITY_TYPE_STANDARD,
                    new RemoteAnimationAdapterCompat(
                            new WrappedLauncherAnimationRunner<>(mWallpaperOpenRunner,
                                    false /* startAtFrontOfQueue */),
                            CLOSING_TRANSITION_DURATION_MS, 0 /* statusBarTransitionDelay */));

            if (KEYGUARD_ANIMATION.get()) {
                mKeyguardGoingAwayRunner = createWallpaperOpenRunner(true /* fromUnlock */);
                definition.addRemoteAnimation(
                        WindowManagerWrapper.TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER,
                        new RemoteAnimationAdapterCompat(
                                new WrappedLauncherAnimationRunner<>(mKeyguardGoingAwayRunner,
                                        true /* startAtFrontOfQueue */),
                                CLOSING_TRANSITION_DURATION_MS, 0 /* statusBarTransitionDelay */));
            }

            new ActivityCompat(mLauncher).registerRemoteAnimations(definition);
        }
    }

    /**
     * Unregisters all remote animations.
     */
    @Override
    public void unregisterRemoteAnimations() {
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        if (hasControlRemoteAppTransitionPermission()) {
            new ActivityCompat(mLauncher).unregisterRemoteAnimations();

            // Also clear strong references to the runners registered with the remote animation
            // definition so we don't have to wait for the system gc
            mWallpaperOpenRunner = null;
            mAppLaunchRunner = null;
            mKeyguardGoingAwayRunner = null;
        }
    }

    private boolean launcherIsATargetWithMode(RemoteAnimationTargetCompat[] targets, int mode) {
        return taskIsATargetWithMode(targets, mLauncher.getTaskId(), mode);
    }

    /**
     * @return Runner that plays when user goes to Launcher
     *         ie. pressing home, swiping up from nav bar.
     */
    WrappedAnimationRunnerImpl createWallpaperOpenRunner(boolean fromUnlock) {
        return new WallpaperOpenLauncherAnimationRunner(mHandler, fromUnlock);
    }

    /**
     * Animator that controls the transformations of the windows when unlocking the device.
     */
    private Animator getUnlockWindowAnimator(RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets) {
        SurfaceTransactionApplier surfaceApplier = new SurfaceTransactionApplier(mDragLayer);
        ValueAnimator unlockAnimator = ValueAnimator.ofFloat(0, 1);
        unlockAnimator.setDuration(CLOSING_TRANSITION_DURATION_MS);
        float cornerRadius = mDeviceProfile.isMultiWindowMode ? 0 :
                QuickStepContract.getWindowCornerRadius(mLauncher.getResources());
        unlockAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                SurfaceParams[] params = new SurfaceParams[appTargets.length];
                for (int i = appTargets.length - 1; i >= 0; i--) {
                    RemoteAnimationTargetCompat target = appTargets[i];
                    params[i] = new SurfaceParams.Builder(target.leash)
                            .withAlpha(1f)
                            .withWindowCrop(target.screenSpaceBounds)
                            .withCornerRadius(cornerRadius)
                            .build();
                }
                surfaceApplier.scheduleApply(params);
            }
        });
        return unlockAnimator;
    }

    /**
     * Animator that controls the transformations of the windows the targets that are closing.
     */
    private Animator getClosingWindowAnimators(RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets) {
        SurfaceTransactionApplier surfaceApplier = new SurfaceTransactionApplier(mDragLayer);
        Matrix matrix = new Matrix();
        Point tmpPos = new Point();
        ValueAnimator closingAnimator = ValueAnimator.ofFloat(0, 1);
        int duration = CLOSING_TRANSITION_DURATION_MS;
        float windowCornerRadius = mDeviceProfile.isMultiWindowMode
                ? 0 : getWindowCornerRadius(mLauncher.getResources());
        closingAnimator.setDuration(duration);
        closingAnimator.addUpdateListener(new MultiValueUpdateListener() {
            FloatProp mDy = new FloatProp(0, mClosingWindowTransY, 0, duration, DEACCEL_1_7);
            FloatProp mScale = new FloatProp(1f, 1f, 0, duration, DEACCEL_1_7);
            FloatProp mAlpha = new FloatProp(1f, 0f, 25, 125, LINEAR);

            @Override
            public void onUpdate(float percent) {
                SurfaceParams[] params = new SurfaceParams[appTargets.length];
                for (int i = appTargets.length - 1; i >= 0; i--) {
                    RemoteAnimationTargetCompat target = appTargets[i];
                    SurfaceParams.Builder builder = new SurfaceParams.Builder(target.leash);

                    tmpPos.set(target.position.x, target.position.y);
                    if (target.localBounds != null) {
                        tmpPos.set(target.localBounds.left, target.localBounds.top);
                    }

                    if (target.mode == MODE_CLOSING) {
                        matrix.setScale(mScale.value, mScale.value,
                                target.screenSpaceBounds.centerX(),
                                target.screenSpaceBounds.centerY());
                        matrix.postTranslate(0, mDy.value);
                        matrix.postTranslate(tmpPos.x, tmpPos.y);
                        builder.withMatrix(matrix)
                                .withAlpha(mAlpha.value)
                                .withCornerRadius(windowCornerRadius);
                    } else {
                        matrix.setTranslate(tmpPos.x, tmpPos.y);
                        builder.withMatrix(matrix)
                                .withAlpha(1f);
                    }
                    final Rect crop = new Rect(target.screenSpaceBounds);
                    crop.offsetTo(0, 0);
                    params[i] = builder
                            .withWindowCrop(crop)
                            .build();
                }
                surfaceApplier.scheduleApply(params);
            }
        });

        return closingAnimator;
    }

    private boolean hasControlRemoteAppTransitionPermission() {
        return mLauncher.checkSelfPermission(CONTROL_REMOTE_APP_TRANSITION_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Remote animation runner for animation from the app to Launcher, including recents.
     */
    protected class WallpaperOpenLauncherAnimationRunner implements WrappedAnimationRunnerImpl {

        private final Handler mHandler;
        private final boolean mFromUnlock;

        public WallpaperOpenLauncherAnimationRunner(Handler handler, boolean fromUnlock) {
            mHandler = handler;
            mFromUnlock = fromUnlock;
        }

        @Override
        public Handler getHandler() {
            return mHandler;
        }

        @Override
        public void onCreateAnimation(RemoteAnimationTargetCompat[] appTargets,
                RemoteAnimationTargetCompat[] wallpaperTargets,
                LauncherAnimationRunner.AnimationResult result) {
            if (mLauncher.isDestroyed()) {
                AnimatorSet anim = new AnimatorSet();
                anim.play(getClosingWindowAnimators(appTargets, wallpaperTargets));
                result.setAnimation(anim, mLauncher.getApplicationContext());
                return;
            }

            if (!mLauncher.hasBeenResumed()) {
                // If launcher is not resumed, wait until new async-frame after resume
                mLauncher.addOnResumeCallback(() ->
                        postAsyncCallback(mHandler, () ->
                                onCreateAnimation(appTargets, wallpaperTargets, result)));
                return;
            }

            if (mLauncher.hasSomeInvisibleFlag(PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION)) {
                mLauncher.addForceInvisibleFlag(INVISIBLE_BY_PENDING_FLAGS);
                mLauncher.getStateManager().moveToRestState();
            }

            AnimatorSet anim = null;
            RemoteAnimationProvider provider = mRemoteAnimationProvider;
            if (provider != null) {
                anim = provider.createWindowAnimation(appTargets, wallpaperTargets);
            }

            if (anim == null) {
                anim = new AnimatorSet();
                anim.play(mFromUnlock
                        ? getUnlockWindowAnimator(appTargets, wallpaperTargets)
                        : getClosingWindowAnimators(appTargets, wallpaperTargets));

                // Normally, we run the launcher content animation when we are transitioning
                // home, but if home is already visible, then we don't want to animate the
                // contents of launcher unless we know that we are animating home as a result
                // of the home button press with quickstep, which will result in launcher being
                // started on touch down, prior to the animation home (and won't be in the
                // targets list because it is already visible). In that case, we force
                // invisibility on touch down, and only reset it after the animation to home
                // is initialized.
                if (launcherIsATargetWithMode(appTargets, MODE_OPENING)
                        || mLauncher.isForceInvisible()) {
                    // Only register the content animation for cancellation when state changes
                    mLauncher.getStateManager().setCurrentAnimation(anim);

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
                        float velocityPxPerS = DynamicResource.provider(mLauncher)
                                .getDimension(R.dimen.unlock_staggered_velocity_dp_per_s);
                        anim.play(new StaggeredWorkspaceAnim(mLauncher, velocityPxPerS, false)
                                .getAnimators());
                    }
                }
            }

            mLauncher.clearForceInvisibleFlag(INVISIBLE_ALL);
            result.setAnimation(anim, mLauncher);
        }
    }

    /**
     * Remote animation runner for animation to launch an app.
     */
    private class AppLaunchAnimationRunner implements WrappedAnimationRunnerImpl {

        private final Handler mHandler;
        private final View mV;

        AppLaunchAnimationRunner(Handler handler, View v) {
            mHandler = handler;
            mV = v;
        }

        @Override
        public Handler getHandler() {
            return mHandler;
        }

        @Override
        public void onCreateAnimation(RemoteAnimationTargetCompat[] appTargets,
                RemoteAnimationTargetCompat[] wallpaperTargets,
                LauncherAnimationRunner.AnimationResult result) {
            AnimatorSet anim = new AnimatorSet();

            boolean launcherClosing =
                    launcherIsATargetWithMode(appTargets, MODE_CLOSING);

            if (isLaunchingFromRecents(mV, appTargets)) {
                composeRecentsLaunchAnimator(anim, mV, appTargets, wallpaperTargets,
                        launcherClosing);
            } else {
                composeIconLaunchAnimator(anim, mV, appTargets, wallpaperTargets,
                        launcherClosing);
            }

            if (launcherClosing) {
                anim.addListener(mForceInvisibleListener);
            }

            result.setAnimation(anim, mLauncher);
        }
    }
}
