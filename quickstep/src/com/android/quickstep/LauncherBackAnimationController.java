/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.util.MathUtils.lerp;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;

import static com.android.launcher3.AbstractFloatingView.TYPE_REBIND_SAFE;
import static com.android.launcher3.BaseActivity.INVISIBLE_ALL;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_PENDING_FLAGS;
import static com.android.launcher3.BaseActivity.PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION;
import static com.android.window.flags.Flags.predictiveBackThreeButtonNav;
import static com.android.window.flags.Flags.removeDepartTargetFromMotion;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.Choreographer;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.window.BackEvent;
import android.window.BackMotionEvent;
import android.window.BackProgressAnimator;
import android.window.IBackAnimationHandoffHandler;
import android.window.IOnBackInvokedCallback;

import com.android.app.animation.Animations;
import com.android.app.animation.Interpolators;
import com.android.internal.policy.SystemBarUtils;
import com.android.internal.view.AppearanceRegion;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.taskbar.LauncherTaskbarUIController;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.quickstep.util.BackAnimState;
import com.android.quickstep.util.ScalingWorkspaceRevealAnim;
import com.android.systemui.shared.system.QuickStepContract;

import java.lang.ref.WeakReference;

/**
 * Controls the animation of swiping back and returning to launcher.
 *
 * This is a two part animation. The first part is an animation that tracks gesture location to
 * scale and move the leaving app window. Once the gesture is committed, the second part takes over
 * the app window and plays the rest of app close transitions in one go.
 *
 * This animation is used only for apps that enable back dispatching via
 * {@link android.window.OnBackInvokedDispatcher}. The controller registers
 * an {@link IOnBackInvokedCallback} with WM Shell and receives back dispatches when a back
 * navigation to launcher starts.
 *
 * Apps using the legacy back dispatching will keep triggering the WALLPAPER_OPEN remote
 * transition registered in {@link QuickstepTransitionManager}.
 *
 */
public class LauncherBackAnimationController {
    private static final int SCRIM_FADE_DURATION = 233;
    private static final float MIN_WINDOW_SCALE =
            Flags.predictiveBackToHomePolish() ? 0.75f : 0.85f;
    private static final float MAX_SCRIM_ALPHA_DARK = 0.8f;
    private static final float MAX_SCRIM_ALPHA_LIGHT = 0.2f;
    private static final int MAX_BLUR_RADIUS = 20;
    private static final int MIN_BLUR_RADIUS_PRE_COMMIT = 10;

    private final QuickstepTransitionManager mQuickstepTransitionManager;
    private final Matrix mTransformMatrix = new Matrix();
    /** The window position at the beginning of the back animation. */
    private final Rect mStartRect = new Rect();
    /** The current window position. */
    private final RectF mCurrentRect = new RectF();
    private final QuickstepLauncher mLauncher;
    private final int mWindowScaleMarginX;
    private float mWindowScaleEndCornerRadius;
    private float mWindowScaleStartCornerRadius;
    private int mStatusBarHeight;
    private final Interpolator mProgressInterpolator = Interpolators.BACK_GESTURE;
    private final Interpolator mVerticalMoveInterpolator = new DecelerateInterpolator();
    private final PointF mInitialTouchPos = new PointF();

    private RemoteAnimationTarget mBackTarget;
    private RemoteAnimationTarget mLauncherTarget;
    private View mLauncherTargetView;
    private final SurfaceControl.Transaction mTransaction = new SurfaceControl.Transaction();
    private float mBackProgress = 0;
    private boolean mBackInProgress = false;
    private boolean mWaitStartTransition = false;
    private OnBackInvokedCallbackStub mBackCallback;
    private IRemoteAnimationFinishedCallback mAnimationFinishedCallback;
    private BackProgressAnimator mProgressAnimator ;
    private SurfaceControl mScrimLayer;
    private ValueAnimator mScrimAlphaAnimator;
    private float mScrimAlpha;
    private boolean mOverridingStatusBarFlags;
    private int mLastBlurRadius = 0;

    private final ComponentCallbacks mComponentCallbacks = new ComponentCallbacks() {
        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            loadResources();
        }

        @Override
        public void onLowMemory() {}
    };

    public LauncherBackAnimationController(
            QuickstepLauncher launcher,
            QuickstepTransitionManager quickstepTransitionManager) {
        mLauncher = launcher;
        mQuickstepTransitionManager = quickstepTransitionManager;
        loadResources();
        mWindowScaleMarginX = mLauncher.getResources().getDimensionPixelSize(
                R.dimen.swipe_back_window_scale_x_margin);

        try {
            mProgressAnimator = new BackProgressAnimator();
        } catch (Throwable t) {
            // Ignore
        }
    }

    /**
     * Registers {@link IOnBackInvokedCallback} to receive back dispatches from shell.
     * @param handler Handler to the thread to run the animations on.
     */
    public void registerBackCallbacks(Handler handler) {
        mBackCallback = new OnBackInvokedCallbackStub(handler, mProgressAnimator,
                mProgressInterpolator, this);
        SystemUiProxy.INSTANCE.get(mLauncher).setBackToLauncherCallback(mBackCallback,
                new RemoteAnimationRunnerStub(this,
                        removeDepartTargetFromMotion() ? handler : null));
    }

    private static class OnBackInvokedCallbackStub extends IOnBackInvokedCallback.Stub {
        private Handler mHandler;
        private BackProgressAnimator mProgressAnimator;
        private final Interpolator mProgressInterpolator;
        // LauncherBackAnimationController has strong reference to Launcher activity, the binder
        // callback should not hold strong reference to it to avoid memory leak.
        private WeakReference<LauncherBackAnimationController> mControllerRef;

        private OnBackInvokedCallbackStub(
                Handler handler,
                BackProgressAnimator progressAnimator,
                Interpolator progressInterpolator,
                LauncherBackAnimationController controller) {
            mHandler = handler;
            mProgressAnimator = progressAnimator;
            mProgressInterpolator = progressInterpolator;
            mControllerRef = new WeakReference<>(controller);
        }

        @Override
        public void onBackCancelled() {
            mHandler.post(() -> {
                LauncherBackAnimationController controller = mControllerRef.get();
                if (controller != null) {
                    mProgressAnimator.onBackCancelled(controller::onCancelFinished);
                }
            });
        }

        @Override
        public void onBackInvoked() {
            mHandler.post(() -> {
                LauncherBackAnimationController controller = mControllerRef.get();
                if (controller != null) {
                    if (!removeDepartTargetFromMotion()) {
                        controller.startTransition();
                    } else {
                        controller.mWaitStartTransition = true;
                        if (controller.mBackTarget != null && controller.mBackInProgress) {
                            controller.startTransition();
                        }
                    }
                }
                mProgressAnimator.reset();
            });
        }

        @Override
        public void onBackProgressed(BackMotionEvent backMotionEvent) {
            mHandler.post(() -> {
                LauncherBackAnimationController controller = mControllerRef.get();
                if (controller == null
                        || controller.mLauncher == null
                        || !controller.mLauncher.isStarted()) {
                    // Skip animating back progress if Launcher isn't visible yet.
                    return;
                }
                mProgressAnimator.onBackProgressed(backMotionEvent);
            });
        }

        @Override
        public void onBackStarted(BackMotionEvent backEvent) {
            mHandler.post(() -> {
                LauncherBackAnimationController controller = mControllerRef.get();
                if (controller != null) {
                    controller.initBackMotion(backEvent);
                    controller.tryStartBackAnimation();
                    mProgressAnimator.onBackStarted(backEvent, event -> {
                        float backProgress = event.getProgress();
                        controller.mBackProgress =
                                mProgressInterpolator.getInterpolation(backProgress);
                        controller.updateBackProgress(controller.mBackProgress, event);
                    });
                }
            });
        }

        @Override
        public void setTriggerBack(boolean triggerBack) {
            // TODO(b/261654570): track touch from the Launcher process.
        }

        @Override
        public void setHandoffHandler(IBackAnimationHandoffHandler unused) {
            // For now, Launcher handles this internally so it doesn't need to hand off the
            // animation.
        }
    }

    private static class RemoteAnimationRunnerStub extends IRemoteAnimationRunner.Stub {

        // LauncherBackAnimationController has strong reference to Launcher activity, the binder
        // callback should not hold strong reference to it to avoid memory leak.
        private WeakReference<LauncherBackAnimationController> mControllerRef;
        private final Handler mHandler;

        private RemoteAnimationRunnerStub(LauncherBackAnimationController controller,
                Handler handler) {
            mControllerRef = new WeakReference<>(controller);
            mHandler = handler;
        }

        @Override
        public void onAnimationStart(int transit, RemoteAnimationTarget[] apps,
                RemoteAnimationTarget[] wallpapers, RemoteAnimationTarget[] nonApps,
                IRemoteAnimationFinishedCallback finishedCallback) {
            LauncherBackAnimationController controller = mControllerRef.get();
            if (controller == null) {
                return;
            }
            final Runnable r = () -> {
                for (final RemoteAnimationTarget target : apps) {
                    if (MODE_CLOSING == target.mode) {
                        controller.mBackTarget = target;
                    }
                    if (MODE_OPENING == target.mode) {
                        controller.mLauncherTarget = target;
                    }
                }
                controller.mAnimationFinishedCallback = finishedCallback;
                if (!removeDepartTargetFromMotion()) {
                    return;
                }
                controller.tryStartBackAnimation();
                if (controller.mWaitStartTransition) {
                    controller.startTransition();
                }
            };
            if (mHandler != null) {
                mHandler.post(r);
            } else {
                r.run();
            }
        }

        @Override
        public void onAnimationCancelled() {}
    }

    private void onCancelFinished() {
        customizeStatusBarAppearance(false);
        if (Flags.predictiveBackToHomePolish() && !mLauncher.getWorkspace().isOverlayShown()
                && !mLauncher.isInState(LauncherState.ALL_APPS)) {
            setLauncherScale(ScalingWorkspaceRevealAnim.MAX_SIZE);
        }
        finishAnimation();
    }

    /** Unregisters the back to launcher callback in shell. */
    public void unregisterBackCallbacks() {
        if (mBackCallback != null) {
            SystemUiProxy.INSTANCE.get(mLauncher).clearBackToLauncherCallback(mBackCallback);
        }
        mProgressAnimator.reset();
        mBackCallback = null;
    }

    private void initBackMotion(BackMotionEvent backEvent) {
        // in case we're still animating an onBackCancelled event, let's remove the finish-
        // callback from the progress animator to prevent calling finishAnimation() before
        // restarting a new animation
        // Side note: initBackMotion is never called during the post-commit phase if the back
        // gesture was committed (not cancelled). BackAnimationController prevents that. Therefore
        // we don't have to handle that case.
        mProgressAnimator.removeOnBackCancelledFinishCallback();

        if (!removeDepartTargetFromMotion()) {
            RemoteAnimationTarget appTarget = backEvent.getDepartingAnimationTarget();
            if (appTarget == null || appTarget.leash == null || !appTarget.leash.isValid()) {
                return;
            }
            mBackTarget = appTarget;
        }
        mBackInProgress = true;
        mInitialTouchPos.set(backEvent.getTouchX(), backEvent.getTouchY());
    }
    private void tryStartBackAnimation() {
        if (mBackTarget == null || (removeDepartTargetFromMotion() && !mBackInProgress)) {
            return;
        }

        mTransaction
                .show(mBackTarget.leash)
                .setAnimationTransaction();
        mStartRect.set(mBackTarget.windowConfiguration.getMaxBounds());

        boolean predictiveBackThreeButtonNav;
        try {
            predictiveBackThreeButtonNav = predictiveBackThreeButtonNav();
        } catch (Throwable t) {
            predictiveBackThreeButtonNav = false;
        }
        // inset bottom in case of taskbar being present
        if (!predictiveBackThreeButtonNav || mLauncher.getDeviceProfile().isTaskbarPresent
                || DisplayController.getNavigationMode(mLauncher) == NavigationMode.NO_BUTTON) {
            mStartRect.inset(0, 0, 0, mBackTarget.contentInsets.bottom);
        }

        mLauncherTargetView = mQuickstepTransitionManager.findLauncherView(
                new RemoteAnimationTarget[]{ mBackTarget });
        setLauncherTargetViewVisible(false);
        mCurrentRect.set(mStartRect);
        if (Flags.predictiveBackToHomePolish() && !mLauncher.getWorkspace().isOverlayShown()
                && !mLauncher.isInState(LauncherState.ALL_APPS)) {
            Animations.cancelOngoingAnimation(mLauncher.getWorkspace());
            Animations.cancelOngoingAnimation(mLauncher.getHotseat());
            if (Flags.predictiveBackToHomeBlur()) {
                mLauncher.getDepthController().pauseBlursOnWindows(true);
            }
            mLauncher.getDepthController().stateDepth.setValue(
                    LauncherState.BACKGROUND_APP.getDepth(mLauncher));
            setLauncherScale(ScalingWorkspaceRevealAnim.MIN_SIZE);
        }
        if (mScrimLayer == null) {
            addScrimLayer();
        }
        applyTransaction();
    }

    private void setLauncherTargetViewVisible(boolean isVisible) {
        if (mLauncherTargetView instanceof BubbleTextView) {
            ((BubbleTextView) mLauncherTargetView).setIconVisible(isVisible);
        } else if (mLauncherTargetView instanceof LauncherAppWidgetHostView) {
            mLauncherTargetView.setAlpha(isVisible ? 1f : 0f);
        }
    }

    private void setLauncherScale(float scale) {
        mLauncher.getWorkspace().setScaleX(scale);
        mLauncher.getWorkspace().setScaleY(scale);
        mLauncher.getHotseat().setScaleX(scale);
        mLauncher.getHotseat().setScaleY(scale);
    }

    void addScrimLayer() {
        SurfaceControl parent = mLauncherTarget != null ? mLauncherTarget.leash : null;
        if (parent == null || !parent.isValid()) {
            // Parent surface is not ready at the moment. Retry later.
            return;
        }
        boolean isDarkTheme = Utilities.isDarkTheme(mLauncher);
        mScrimLayer = new SurfaceControl.Builder()
                .setName("Back to launcher background scrim")
                .setCallsite("LauncherBackAnimationController")
                .setColorLayer()
                .setParent(parent)
                .setOpaque(false)
                .setHidden(false)
                .build();
        final float[] colorComponents = new float[] { 0f, 0f, 0f };
        mScrimAlpha = (isDarkTheme)
                ? MAX_SCRIM_ALPHA_DARK : MAX_SCRIM_ALPHA_LIGHT;
        setBlur(MAX_BLUR_RADIUS);
        mTransaction
                .setColor(mScrimLayer, colorComponents)
                .setAlpha(mScrimLayer, mScrimAlpha)
                .show(mScrimLayer)
                // Ensure the scrim layer occludes opening task & wallpaper
                .setLayer(mScrimLayer, 1000);
    }

    void removeScrimLayer() {
        if (mScrimLayer == null) {
            return;
        }
        if (mScrimLayer.isValid()) {
            mTransaction.remove(mScrimLayer);
            applyTransaction();
        }
        mScrimLayer = null;
    }

    private void updateBackProgress(float progress, BackEvent event) {
        if (!mBackInProgress || mBackTarget == null) {
            return;
        }
        if (mScrimLayer == null) {
            // Scrim hasn't been attached yet. Let's attach it.
            addScrimLayer();
        } else {
            mLastBlurRadius = (int) lerp(MAX_BLUR_RADIUS, MIN_BLUR_RADIUS_PRE_COMMIT, progress);
            setBlur(mLastBlurRadius);
        }
        float screenWidth = mStartRect.width();
        float screenHeight = mStartRect.height();
        float width = Utilities.mapRange(progress, 1, MIN_WINDOW_SCALE) * screenWidth;
        float height = screenHeight / screenWidth * width;

        // Base the window movement in the Y axis on the touch movement in the Y axis.
        float rawYDelta = event.getTouchY() - mInitialTouchPos.y;
        float yDirection = rawYDelta < 0 ? -1 : 1;
        // limit yDelta interpretation to 1/2 of screen height in either direction
        float deltaYRatio = Math.min(screenHeight / 2f, Math.abs(rawYDelta)) / (screenHeight / 2f);
        float interpolatedYRatio = mVerticalMoveInterpolator.getInterpolation(deltaYRatio);
        // limit y-shift so surface never passes 8dp screen margin
        float deltaY = yDirection * interpolatedYRatio * Math.max(0f, (screenHeight - height)
                / 2f - mWindowScaleMarginX);
        // Move the window along the Y axis.
        float top = (screenHeight - height) * 0.5f + deltaY;
        // Move the window along the X axis.
        float left = switch (event.getSwipeEdge()) {
            case BackEvent.EDGE_RIGHT -> progress * mWindowScaleMarginX;
            case BackEvent.EDGE_LEFT -> screenWidth - progress * mWindowScaleMarginX - width;
            default -> (screenWidth - width) / 2;
        };
        mCurrentRect.set(left, top, left + width, top + height);
        float cornerRadius = Utilities.mapRange(
                progress, mWindowScaleStartCornerRadius, mWindowScaleEndCornerRadius);
        applyTransform(mCurrentRect, cornerRadius);

        customizeStatusBarAppearance(top > mStatusBarHeight / 2);
    }

    private void setBlur(int blurRadius) {
        if (Flags.predictiveBackToHomeBlur()) {
            mTransaction.setBackgroundBlurRadius(mScrimLayer, blurRadius);
        }
    }

    /** Transform the target window to match the target rect. */
    private void applyTransform(RectF targetRect, float cornerRadius) {
        final float scale = targetRect.width() / mStartRect.width();
        mTransformMatrix.reset();
        mTransformMatrix.setScale(scale, scale);
        mTransformMatrix.postTranslate(targetRect.left, targetRect.top);

        if (mBackTarget.leash.isValid()) {
            mTransaction.setMatrix(mBackTarget.leash, mTransformMatrix, new float[9]);
            mTransaction.setWindowCrop(mBackTarget.leash, mStartRect);
            mTransaction.setCornerRadius(mBackTarget.leash, cornerRadius);
        }
        applyTransaction();
    }

    private void applyTransaction() {
        mTransaction.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        mTransaction.apply();
    }

    private void startTransition() {
        if (!removeDepartTargetFromMotion()) {
            if (mBackTarget == null) {
                // Trigger transition system instead of custom transition animation.
                finishAnimation();
                return;
            }
        } else {
            mWaitStartTransition = false;
        }
        if (mLauncher.isDestroyed()) {
            return;
        }
        mLauncher.setPredictiveBackToHomeInProgress(true);
        LauncherTaskbarUIController taskbarUIController = mLauncher.getTaskbarUIController();
        if (taskbarUIController != null) {
            taskbarUIController.onLauncherVisibilityChanged(true);
        }
        // TODO: Catch the moment when launcher becomes visible after the top app un-occludes
        //  launcher and start animating afterwards. Currently we occasionally get a flicker from
        //  animating when launcher is still invisible.
        if (mLauncher.hasSomeInvisibleFlag(PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION)) {
            mLauncher.addForceInvisibleFlag(INVISIBLE_BY_PENDING_FLAGS);
            mLauncher.getStateManager().moveToRestState();
        }

        setLauncherTargetViewVisible(true);

        // Explicitly close opened floating views (which is typically called from
        // Launcher#onResumed, but in the predictive back flow launcher is not resumed until
        // the transition is fully finished.)
        AbstractFloatingView.closeAllOpenViewsExcept(mLauncher, false, TYPE_REBIND_SAFE);
        float cornerRadius = Utilities.mapRange(
                mBackProgress, mWindowScaleStartCornerRadius, mWindowScaleEndCornerRadius);
        final RectF resolveRectF = new RectF();
        mQuickstepTransitionManager.transferRectToTargetCoordinate(
                mBackTarget, mCurrentRect, true, resolveRectF);

        BackAnimState backAnim =
                mQuickstepTransitionManager.createWallpaperOpenAnimations(
                    new RemoteAnimationTarget[]{mBackTarget},
                    new RemoteAnimationTarget[0],
                    new RemoteAnimationTarget[0],
                    resolveRectF,
                    cornerRadius,
                    mBackInProgress /* fromPredictiveBack */);
        startTransitionAnimations(backAnim);
        mLauncher.clearForceInvisibleFlag(INVISIBLE_ALL);
        customizeStatusBarAppearance(true);
    }

    private void finishAnimation() {
        mLauncher.setPredictiveBackToHomeInProgress(false);
        if (mBackTarget != null && mBackTarget.leash.isValid()) {
            mBackTarget.leash.release();
        }
        mBackTarget = null;
        if (mLauncherTarget != null && mLauncherTarget.leash.isValid()) {
            mLauncherTarget.leash.release();
        }
        mLauncherTarget = null;
        mBackInProgress = false;
        mBackProgress = 0;
        mTransformMatrix.reset();
        mCurrentRect.setEmpty();
        mStartRect.setEmpty();
        mInitialTouchPos.set(0, 0);
        setLauncherTargetViewVisible(true);
        mLauncherTargetView = null;
        // We don't call customizeStatusBarAppearance here to prevent the status bar update with
        // the legacy appearance. It should be refreshed after the transition done.
        mOverridingStatusBarFlags = false;
        if (mAnimationFinishedCallback != null) {
            try {
                mAnimationFinishedCallback.onAnimationFinished();
            } catch (RemoteException e) {
                Log.w("ShellBackPreview", "Failed call onBackAnimationFinished", e);
            }
            mAnimationFinishedCallback = null;
        }
        if (mScrimAlphaAnimator != null && mScrimAlphaAnimator.isRunning()) {
            mScrimAlphaAnimator.cancel();
            mScrimAlphaAnimator = null;
        }
        if (mScrimLayer != null) {
            removeScrimLayer();
        }
        if (Flags.predictiveBackToHomePolish() && Flags.predictiveBackToHomeBlur()
                && !mLauncher.getWorkspace().isOverlayShown()
                && !mLauncher.isInState(LauncherState.ALL_APPS)) {
            mLauncher.getDepthController().pauseBlursOnWindows(false);
        }
        mLastBlurRadius = 0;
    }

    private void startTransitionAnimations(BackAnimState backAnim) {
        backAnim.addOnAnimCompleteCallback(this::finishAnimation);
        if (mScrimLayer == null) {
            // Scrim hasn't been attached yet. Let's attach it.
            addScrimLayer();
        }
        mScrimAlphaAnimator = new ValueAnimator().ofFloat(1, 0);
        mScrimAlphaAnimator.addUpdateListener(animation -> {
            float value = (Float) animation.getAnimatedValue();
            if (mScrimLayer != null && mScrimLayer.isValid()) {
                mTransaction.setAlpha(mScrimLayer, value * mScrimAlpha);
                setBlur((int) lerp(mLastBlurRadius, 0, 1f - value));
                applyTransaction();
            }
        });
        mScrimAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                resetScrim();
            }
        });
        mScrimAlphaAnimator.setDuration(SCRIM_FADE_DURATION).start();
        backAnim.start();
    }

    private void loadResources() {
        mWindowScaleEndCornerRadius = QuickStepContract.supportsRoundedCornersOnWindows(
                mLauncher.getResources())
                ? mLauncher.getResources().getDimensionPixelSize(
                R.dimen.swipe_back_window_corner_radius)
                : 0;
        mWindowScaleStartCornerRadius = QuickStepContract.getWindowCornerRadius(mLauncher);
//        mStatusBarHeight = SystemBarUtils.getStatusBarHeight(mLauncher);
        mStatusBarHeight = 24;
    }

    /**
     * Called when launcher is destroyed. Unregisters component callbacks to avoid memory leaks.
     */
    public void unregisterComponentCallbacks() {
        mLauncher.unregisterComponentCallbacks(mComponentCallbacks);
    }

    /**
     * Registers component callbacks with the launcher to receive configuration change events.
     */
    public void registerComponentCallbacks() {
        mLauncher.registerComponentCallbacks(mComponentCallbacks);
    }


    private void resetScrim() {
        removeScrimLayer();
        mScrimAlpha = 0;
    }

    private void customizeStatusBarAppearance(boolean overridingStatusBarFlags) {
        if (mOverridingStatusBarFlags == overridingStatusBarFlags) {
            return;
        }

        mOverridingStatusBarFlags = overridingStatusBarFlags;
        final boolean isBackgroundDark =
                (mLauncher.getWindow().getDecorView().getSystemUiVisibility()
                        & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) == 0;
        final AppearanceRegion region = mOverridingStatusBarFlags
                ? new AppearanceRegion(!isBackgroundDark ? APPEARANCE_LIGHT_STATUS_BARS : 0,
                        mBackTarget.windowConfiguration.getBounds())
                : null;
        SystemUiProxy.INSTANCE.get(mLauncher).customizeStatusBarAppearance(region);
    }
}
