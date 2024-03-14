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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.provider.Settings.Secure.LAUNCHER_TASKBAR_EDUCATION_SHOWING;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_NONE;
import static android.window.StartingWindowInfo.STARTING_WINDOW_TYPE_SPLASH_SCREEN;
import static android.window.TransitionFilter.CONTAINER_ORDER_TOP;

import static com.android.app.animation.Interpolators.ACCELERATE_1_5;
import static com.android.app.animation.Interpolators.AGGRESSIVE_EASE;
import static com.android.app.animation.Interpolators.DECELERATE_1_5;
import static com.android.app.animation.Interpolators.DECELERATE_1_7;
import static com.android.app.animation.Interpolators.EXAGGERATED_EASE;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.BaseActivity.INVISIBLE_ALL;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_APP_TRANSITIONS;
import static com.android.launcher3.BaseActivity.INVISIBLE_BY_PENDING_FLAGS;
import static com.android.launcher3.BaseActivity.PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION;
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.VIEW_BACKGROUND_COLOR;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.Utilities.mapBoundToRange;
import static com.android.launcher3.config.FeatureFlags.ENABLE_BACK_SWIPE_HOME_ANIMATION;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SCRIM_FOR_APP_LAUNCH;
import static com.android.launcher3.config.FeatureFlags.KEYGUARD_ANIMATION;
import static com.android.launcher3.config.FeatureFlags.SEPARATE_RECENTS_ACTIVITY;
import static com.android.launcher3.model.data.ItemInfo.NO_MATCHING_ID;
import static com.android.launcher3.testing.shared.TestProtocol.WALLPAPER_OPEN_ANIMATION_FINISHED_MESSAGE;
import static com.android.launcher3.util.DisplayController.isTransientTaskbar;
import static com.android.launcher3.util.Executors.ORDERED_BG_EXECUTOR;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.launcher3.views.FloatingIconView.getFloatingIconView;
import static com.android.quickstep.TaskAnimationManager.ENABLE_SHELL_TRANSITIONS;
import static com.android.quickstep.TaskViewUtils.findTaskViewToLaunch;
import static com.android.quickstep.util.AnimUtils.clampToDuration;
import static com.android.quickstep.util.AnimUtils.completeRunnableListCallback;
import static com.android.systemui.shared.system.QuickStepContract.getWindowCornerRadius;
import static com.android.systemui.shared.system.QuickStepContract.supportsRoundedCornersOnWindows;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.util.Pair;
import android.util.Size;
import android.view.CrossWindowBlurListeners;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationDefinition;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.window.RemoteTransition;
import android.window.TransitionFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.internal.jank.Cuj;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.LauncherAnimationRunner.RemoteAnimationFactory;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.shortcuts.DeepShortcutView;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.taskbar.LauncherTaskbarUIController;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.DynamicResource;
import com.android.launcher3.util.ObjectWrapper;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.FloatingIconView;
import com.android.launcher3.views.ScrimView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.quickstep.LauncherBackAnimationController;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskViewUtils;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.RectFSpringAnim.DefaultSpringConfig;
import com.android.quickstep.util.RectFSpringAnim.TaskbarHotseatSpringConfig;
import com.android.quickstep.util.StaggeredWorkspaceAnim;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskRestartedDuringLaunchListener;
import com.android.quickstep.util.WorkspaceRevealAnim;
import com.android.quickstep.views.FloatingWidgetView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.animation.DelegateTransitionAnimatorController;
import com.android.systemui.animation.LaunchableView;
import com.android.systemui.animation.RemoteAnimationDelegate;
import com.android.systemui.animation.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.BlurUtils;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.wm.shell.startingsurface.IStartingWindowListener;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Manages the opening and closing app transitions from Launcher
 */
public class QuickstepTransitionManager implements OnDeviceProfileChangeListener {

    private static final boolean ENABLE_SHELL_STARTING_SURFACE =
            SystemProperties.getBoolean("persist.debug.shell_starting_surface", true);

    /** Duration of status bar animations. */
    public static final int STATUS_BAR_TRANSITION_DURATION = 120;

    /**
     * Since our animations decelerate heavily when finishing, we want to start status bar
     * animations x ms before the ending.
     */
    public static final int STATUS_BAR_TRANSITION_PRE_DELAY = 96;

    public static final long APP_LAUNCH_DURATION = 500;

    private static final long APP_LAUNCH_ALPHA_DURATION = 50;
    private static final long APP_LAUNCH_ALPHA_START_DELAY = 25;

    public static final int ANIMATION_NAV_FADE_IN_DURATION = 266;
    public static final int ANIMATION_NAV_FADE_OUT_DURATION = 133;
    public static final long ANIMATION_DELAY_NAV_FADE_IN =
            APP_LAUNCH_DURATION - ANIMATION_NAV_FADE_IN_DURATION;
    public static final Interpolator NAV_FADE_IN_INTERPOLATOR =
            new PathInterpolator(0f, 0f, 0f, 1f);
    public static final Interpolator NAV_FADE_OUT_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 1f, 1f);

    public static final int RECENTS_LAUNCH_DURATION = 336;
    private static final int LAUNCHER_RESUME_START_DELAY = 100;
    private static final int CLOSING_TRANSITION_DURATION_MS = 250;
    public static final int SPLIT_LAUNCH_DURATION = 370;
    public static final int SPLIT_DIVIDER_ANIM_DURATION = 100;

    public static final int CONTENT_ALPHA_DURATION = 217;
    public static final int TRANSIENT_TASKBAR_TRANSITION_DURATION = 417;
    public static final int TASKBAR_TO_APP_DURATION = 600;
    // TODO(b/236145847): Tune TASKBAR_TO_HOME_DURATION to 383 after conflict with unlock animation
    // is solved.
    private static final int TASKBAR_TO_HOME_DURATION_FAST = 300;
    private static final int TASKBAR_TO_HOME_DURATION_SLOW = 1000;
    protected static final int CONTENT_SCALE_DURATION = 350;
    protected static final int CONTENT_SCRIM_DURATION = 350;

    private static final int MAX_NUM_TASKS = 5;

    // Cross-fade duration between App Widget and App
    private static final int WIDGET_CROSSFADE_DURATION_MILLIS = 125;

    protected final QuickstepLauncher mLauncher;
    protected final DragLayer mDragLayer;

    protected final Handler mHandler;

    private final float mClosingWindowTransY;
    private final float mMaxShadowRadius;

    private final StartingWindowListener mStartingWindowListener =
            new StartingWindowListener(this);
    private ContentObserver mAnimationRemovalObserver = new ContentObserver(
            ORDERED_BG_EXECUTOR.getHandler()) {
        @Override
        public void onChange(boolean selfChange) {
            mAreAnimationsEnabled = Global.getFloat(mLauncher.getContentResolver(),
                    Global.ANIMATOR_DURATION_SCALE, 1f) > 0
                    || Global.getFloat(mLauncher.getContentResolver(),
                    Global.TRANSITION_ANIMATION_SCALE, 1f) > 0;
        }
    };

    private DeviceProfile mDeviceProfile;

    // Strong refs to runners which are cleared when the launcher activity is destroyed
    private RemoteAnimationFactory mWallpaperOpenRunner;
    private RemoteAnimationFactory mAppLaunchRunner;
    private RemoteAnimationFactory mKeyguardGoingAwayRunner;

    private RemoteAnimationFactory mWallpaperOpenTransitionRunner;
    private RemoteTransition mLauncherOpenTransition;

    private final RemoteAnimationCoordinateTransfer mCoordinateTransfer;

    private LauncherBackAnimationController mBackAnimationController;
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

    // Pairs of window starting type and starting window background color for starting tasks
    // Will never be larger than MAX_NUM_TASKS
    private LinkedHashMap<Integer, Pair<Integer, Integer>> mTaskStartParams;
    private boolean mAreAnimationsEnabled = true;

    private final Interpolator mOpeningXInterpolator;
    private final Interpolator mOpeningInterpolator;

    public QuickstepTransitionManager(Context context) {
        mLauncher = Launcher.cast(Launcher.getLauncher(context));
        mDragLayer = mLauncher.getDragLayer();
        mHandler = new Handler(Looper.getMainLooper());
        mDeviceProfile = mLauncher.getDeviceProfile();
        mBackAnimationController = new LauncherBackAnimationController(mLauncher, this);
        checkAndMonitorIfAnimationsAreEnabled();

        Resources res = mLauncher.getResources();
        mClosingWindowTransY = res.getDimensionPixelSize(R.dimen.closing_window_trans_y);
        mMaxShadowRadius = res.getDimensionPixelSize(R.dimen.max_shadow_radius);

        mLauncher.addOnDeviceProfileChangeListener(this);

        if (ENABLE_SHELL_STARTING_SURFACE) {
            mTaskStartParams = new LinkedHashMap<>(MAX_NUM_TASKS) {
                @Override
                protected boolean removeEldestEntry(Entry<Integer, Pair<Integer, Integer>> entry) {
                    return size() > MAX_NUM_TASKS;
                }
            };

            SystemUiProxy.INSTANCE.get(mLauncher).setStartingWindowListener(
                    mStartingWindowListener);
        }

        mOpeningXInterpolator = AnimationUtils.loadInterpolator(context, R.interpolator.app_open_x);
        mOpeningInterpolator = AnimationUtils.loadInterpolator(context,
                R.interpolator.emphasized_interpolator);
        mCoordinateTransfer = new RemoteAnimationCoordinateTransfer(mLauncher);
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        mDeviceProfile = dp;
    }

    /**
     * @return ActivityOptions with remote animations that controls how the window of the opening
     * targets are displayed.
     */
    public ActivityOptionsWrapper getActivityLaunchOptions(View v) {
        boolean fromRecents = isLaunchingFromRecents(v, null /* targets */);
        RunnableList onEndCallback = new RunnableList();

        // Handle the case where an already visible task is launched which results in no transition
        TaskRestartedDuringLaunchListener restartedListener =
                new TaskRestartedDuringLaunchListener();
        restartedListener.register(onEndCallback::executeAllAndDestroy);
        onEndCallback.add(restartedListener::unregister);

        mAppLaunchRunner = new AppLaunchAnimationRunner(v, onEndCallback);
        ItemInfo tag = (ItemInfo) v.getTag();
        if (tag != null && tag.shouldUseBackgroundAnimation()) {
            ContainerAnimationRunner containerAnimationRunner = ContainerAnimationRunner.from(
                    v, mLauncher, mStartingWindowListener, onEndCallback);
            if (containerAnimationRunner != null) {
                mAppLaunchRunner = containerAnimationRunner;
            }
        }
        RemoteAnimationRunnerCompat runner = new LauncherAnimationRunner(
                mHandler, mAppLaunchRunner, true /* startAtFrontOfQueue */);

        // Note that this duration is a guess as we do not know if the animation will be a
        // recents launch or not for sure until we know the opening app targets.
        long duration = fromRecents
                ? RECENTS_LAUNCH_DURATION
                : APP_LAUNCH_DURATION;

        long statusBarTransitionDelay = duration - STATUS_BAR_TRANSITION_DURATION
                - STATUS_BAR_TRANSITION_PRE_DELAY;
        ActivityOptions options = ActivityOptions.makeRemoteAnimation(
                new RemoteAnimationAdapter(runner, duration, statusBarTransitionDelay),
                new RemoteTransition(runner.toRemoteTransition(),
                        mLauncher.getIApplicationThread(), "QuickstepLaunch"));
        IRemoteCallback endCallback = completeRunnableListCallback(onEndCallback);
        options.setOnAnimationAbortListener(endCallback);
        options.setOnAnimationFinishedListener(endCallback);
        return new ActivityOptionsWrapper(options, onEndCallback);
    }

    /**
     * Whether the launch is a recents app transition and we should do a launch animation
     * from the recents view. Note that if the remote animation targets are not provided, this
     * may not always be correct as we may resolve the opening app to a task when the animation
     * starts.
     *
     * @param v       the view to launch from
     * @param targets apps that are opening/closing
     * @return true if the app is launching from recents, false if it most likely is not
     */
    protected boolean isLaunchingFromRecents(@NonNull View v,
            @Nullable RemoteAnimationTarget[] targets) {
        return mLauncher.getStateManager().getState().overviewUi
                && findTaskViewToLaunch(mLauncher.getOverviewPanel(), v, targets) != null;
    }

    /**
     * Composes the animations for a launch from the recents list.
     *
     * @param anim            the animator set to add to
     * @param v               the launching view
     * @param appTargets      the apps that are opening/closing
     * @param launcherClosing true if the launcher app is closing
     */
    protected void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTarget[] appTargets,
            @NonNull RemoteAnimationTarget[] wallpaperTargets,
            @NonNull RemoteAnimationTarget[] nonAppTargets, boolean launcherClosing) {
        TaskViewUtils.composeRecentsLaunchAnimator(anim, v, appTargets, wallpaperTargets,
                nonAppTargets, launcherClosing, mLauncher.getStateManager(),
                mLauncher.getOverviewPanel(), mLauncher.getDepthController());
    }

    private boolean areAllTargetsTranslucent(@NonNull RemoteAnimationTarget[] targets) {
        boolean isAllOpeningTargetTrs = true;
        for (int i = 0; i < targets.length; i++) {
            RemoteAnimationTarget target = targets[i];
            if (target.mode == MODE_OPENING) {
                isAllOpeningTargetTrs &= target.isTranslucent;
            }
            if (!isAllOpeningTargetTrs) break;
        }
        return isAllOpeningTargetTrs;
    }

    /**
     * Compose the animations for a launch from the app icon.
     *
     * @param anim            the animation to add to
     * @param v               the launching view with the icon
     * @param appTargets      the list of opening/closing apps
     * @param launcherClosing true if launcher is closing
     */
    private void composeIconLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTarget[] appTargets,
            @NonNull RemoteAnimationTarget[] wallpaperTargets,
            @NonNull RemoteAnimationTarget[] nonAppTargets,
            boolean launcherClosing) {
        // Set the state animation first so that any state listeners are called
        // before our internal listeners.
        mLauncher.getStateManager().setCurrentAnimation(anim);

        // Note: the targetBounds are relative to the launcher
        int startDelay = getSingleFrameMs(mLauncher);
        Animator windowAnimator = getOpeningWindowAnimators(
                v, appTargets, wallpaperTargets, nonAppTargets, launcherClosing);
        windowAnimator.setStartDelay(startDelay);
        anim.play(windowAnimator);
        if (launcherClosing) {
            // Delay animation by a frame to avoid jank.
            Pair<AnimatorSet, Runnable> launcherContentAnimator =
                    getLauncherContentAnimator(true /* isAppOpening */, startDelay, false);
            anim.play(launcherContentAnimator.first);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    launcherContentAnimator.second.run();
                }
            });
        }
    }

    private void composeWidgetLaunchAnimator(
            @NonNull AnimatorSet anim,
            @NonNull LauncherAppWidgetHostView v,
            @NonNull RemoteAnimationTarget[] appTargets,
            @NonNull RemoteAnimationTarget[] wallpaperTargets,
            @NonNull RemoteAnimationTarget[] nonAppTargets,
            boolean launcherClosing) {
        mLauncher.getStateManager().setCurrentAnimation(anim);
        anim.play(getOpeningWindowAnimatorsForWidget(
                v, appTargets, wallpaperTargets, nonAppTargets, launcherClosing));
    }

    /**
     * Return the window bounds of the opening target.
     * In multiwindow mode, we need to get the final size of the opening app window target to help
     * figure out where the floating view should animate to.
     */
    private Rect getWindowTargetBounds(@NonNull RemoteAnimationTarget[] appTargets,
            int rotationChange) {
        RemoteAnimationTarget target = null;
        for (RemoteAnimationTarget t : appTargets) {
            if (t.mode != MODE_OPENING) continue;
            target = t;
            break;
        }
        if (target == null) return new Rect(0, 0, mDeviceProfile.widthPx, mDeviceProfile.heightPx);
        final Rect bounds = new Rect(target.screenSpaceBounds);
        if (target.localBounds != null) {
            bounds.set(target.localBounds);
        } else {
            bounds.offsetTo(target.position.x, target.position.y);
        }
        if (rotationChange != 0) {
            if ((rotationChange % 2) == 1) {
                // undoing rotation, so our "original" parent size is actually flipped
                Utilities.rotateBounds(bounds, mDeviceProfile.heightPx, mDeviceProfile.widthPx,
                        4 - rotationChange);
            } else {
                Utilities.rotateBounds(bounds, mDeviceProfile.widthPx, mDeviceProfile.heightPx,
                        4 - rotationChange);
            }
        }
        if (mDeviceProfile.isTaskbarPresentInApps
                && !target.willShowImeOnTarget
                && !isTransientTaskbar(mLauncher)) {
            // Animate to above the taskbar.
            bounds.bottom -= target.contentInsets.bottom;
        }
        return bounds;
    }

    /** Dump debug logs to bug report. */
    public void dump(@NonNull String prefix, @NonNull PrintWriter printWriter) {}

    /**
     * Content is everything on screen except the background and the floating view (if any).
     *
     * @param isAppOpening     True when this is called when an app is opening.
     *                         False when this is called when an app is closing.
     * @param startDelay       Start delay duration.
     * @param skipAllAppsScale True if we want to avoid scaling All Apps
     */
    private Pair<AnimatorSet, Runnable> getLauncherContentAnimator(boolean isAppOpening,
            int startDelay, boolean skipAllAppsScale) {
        AnimatorSet launcherAnimator = new AnimatorSet();
        Runnable endListener;

        float[] alphas = isAppOpening
                ? new float[]{1, 0}
                : new float[]{0, 1};

        float[] scales = isAppOpening
                ? new float[]{1, mDeviceProfile.workspaceContentScale}
                : new float[]{mDeviceProfile.workspaceContentScale, 1};

        // Pause expensive view updates as they can lead to layer thrashing and skipped frames.
        mLauncher.pauseExpensiveViewUpdates();

        if (mLauncher.isInState(ALL_APPS)) {
            // All Apps in portrait mode is full screen, so we only animate AllAppsContainerView.
            final View appsView = mLauncher.getAppsView();
            final float startAlpha = appsView.getAlpha();
            final float startScale = SCALE_PROPERTY.get(appsView);
            if (mDeviceProfile.isTablet) {
                // AllApps should not fade at all in tablets.
                alphas = new float[]{1, 1};
            }
            appsView.setAlpha(alphas[0]);

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

            if (!skipAllAppsScale) {
                SCALE_PROPERTY.set(appsView, scales[0]);
                ObjectAnimator scale = ObjectAnimator.ofFloat(appsView, SCALE_PROPERTY, scales);
                scale.setInterpolator(AGGRESSIVE_EASE);
                scale.setDuration(CONTENT_SCALE_DURATION);
                launcherAnimator.play(scale);
            }

            launcherAnimator.play(alpha);

            endListener = () -> {
                appsView.setAlpha(startAlpha);
                SCALE_PROPERTY.set(appsView, startScale);
                appsView.setLayerType(View.LAYER_TYPE_NONE, null);
                mLauncher.resumeExpensiveViewUpdates();
            };
        } else if (mLauncher.isInState(OVERVIEW)) {
            endListener = composeViewContentAnimator(launcherAnimator, alphas, scales);
        } else {
            List<View> viewsToAnimate = new ArrayList<>();
            Workspace<?> workspace = mLauncher.getWorkspace();
            workspace.forEachVisiblePage(
                    view -> viewsToAnimate.add(((CellLayout) view).getShortcutsAndWidgets()));

            // Do not scale hotseat as a whole when taskbar is present, and scale QSB only if it's
            // not inline.
            if (mDeviceProfile.isTaskbarPresent) {
                if (!mDeviceProfile.isQsbInline) {
                    viewsToAnimate.add(mLauncher.getHotseat().getQsb());
                }
            } else {
                viewsToAnimate.add(mLauncher.getHotseat());
            }

            viewsToAnimate.forEach(view -> {
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                ObjectAnimator scaleAnim = ObjectAnimator.ofFloat(view, SCALE_PROPERTY, scales)
                        .setDuration(CONTENT_SCALE_DURATION);
                scaleAnim.setInterpolator(DECELERATE_1_5);
                launcherAnimator.play(scaleAnim);
            });

            final boolean scrimEnabled = ENABLE_SCRIM_FOR_APP_LAUNCH.get();
            if (scrimEnabled) {
                int scrimColor = Themes.getAttrColor(mLauncher, R.attr.overviewScrimColor);
                int scrimColorTrans = ColorUtils.setAlphaComponent(scrimColor, 0);
                int[] colors = isAppOpening
                        ? new int[]{scrimColorTrans, scrimColor}
                        : new int[]{scrimColor, scrimColorTrans};
                ScrimView scrimView = mLauncher.getScrimView();
                if (scrimView.getBackground() instanceof ColorDrawable) {
                    scrimView.setBackgroundColor(colors[0]);

                    ObjectAnimator scrim = ObjectAnimator.ofArgb(scrimView, VIEW_BACKGROUND_COLOR,
                            colors);
                    scrim.setDuration(CONTENT_SCRIM_DURATION);
                    scrim.setInterpolator(DECELERATE_1_5);

                    launcherAnimator.play(scrim);
                }
            }

            endListener = () -> {
                viewsToAnimate.forEach(view -> {
                    SCALE_PROPERTY.set(view, 1f);
                    view.setLayerType(View.LAYER_TYPE_NONE, null);
                });
                if (scrimEnabled) {
                    mLauncher.getScrimView().setBackgroundColor(Color.TRANSPARENT);
                }
                mLauncher.resumeExpensiveViewUpdates();
            };
        }

        launcherAnimator.setStartDelay(startDelay);
        return new Pair<>(launcherAnimator, endListener);
    }

    /**
     * Compose recents view alpha and translation Y animation when launcher opens/closes apps.
     *
     * @param anim   the animator set to add to
     * @param alphas the alphas to animate to over time
     * @param scales the scale values to animator to over time
     * @return listener to run when the animation ends
     */
    protected Runnable composeViewContentAnimator(@NonNull AnimatorSet anim,
            float[] alphas, float[] scales) {
        RecentsView overview = mLauncher.getOverviewPanel();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(overview,
                RecentsView.CONTENT_ALPHA, alphas);
        alpha.setDuration(CONTENT_ALPHA_DURATION);
        alpha.setInterpolator(LINEAR);
        anim.play(alpha);
        overview.setFreezeViewVisibility(true);

        ObjectAnimator scaleAnim = ObjectAnimator.ofFloat(overview, SCALE_PROPERTY, scales);
        scaleAnim.setInterpolator(AGGRESSIVE_EASE);
        scaleAnim.setDuration(CONTENT_SCALE_DURATION);
        anim.play(scaleAnim);

        return () -> {
            overview.setFreezeViewVisibility(false);
            SCALE_PROPERTY.set(overview, 1f);
            mLauncher.getStateManager().reapplyState();
            mLauncher.resumeExpensiveViewUpdates();
        };
    }

    /**
     * @return Animator that controls the window of the opening targets from app icons.
     */
    private Animator getOpeningWindowAnimators(View v,
            RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpaperTargets,
            RemoteAnimationTarget[] nonAppTargets,
            boolean launcherClosing) {
        int rotationChange = getRotationChange(appTargets);
        Rect windowTargetBounds = getWindowTargetBounds(appTargets, rotationChange);
        boolean appTargetsAreTranslucent = areAllTargetsTranslucent(appTargets);

        RectF launcherIconBounds = new RectF();
        FloatingIconView floatingView = getFloatingIconView(mLauncher, v,
                (mLauncher.getTaskbarUIController() == null || !isTransientTaskbar(mLauncher))
                        ? null
                        : mLauncher.getTaskbarUIController().findMatchingView(v),
                null /* fadeOutView */, !appTargetsAreTranslucent, launcherIconBounds,
                true /* isOpening */);
        Rect crop = new Rect();
        Matrix matrix = new Matrix();

        RemoteAnimationTargets openingTargets = new RemoteAnimationTargets(appTargets,
                wallpaperTargets, nonAppTargets, MODE_OPENING);
        SurfaceTransactionApplier surfaceApplier =
                new SurfaceTransactionApplier(floatingView);
        openingTargets.addReleaseCheck(surfaceApplier);
        RemoteAnimationTarget navBarTarget = openingTargets.getNavBarRemoteAnimationTarget();

        int[] dragLayerBounds = new int[2];
        mDragLayer.getLocationOnScreen(dragLayerBounds);

        final boolean hasSplashScreen;
        if (ENABLE_SHELL_STARTING_SURFACE) {
            int taskId = openingTargets.getFirstAppTargetTaskId();
            Pair<Integer, Integer> defaultParams = Pair.create(STARTING_WINDOW_TYPE_NONE, 0);
            Pair<Integer, Integer> taskParams =
                    mTaskStartParams.getOrDefault(taskId, defaultParams);
            mTaskStartParams.remove(taskId);
            hasSplashScreen = taskParams.first == STARTING_WINDOW_TYPE_SPLASH_SCREEN;
        } else {
            hasSplashScreen = false;
        }

        AnimOpenProperties prop = new AnimOpenProperties(mLauncher.getResources(), mDeviceProfile,
                windowTargetBounds, launcherIconBounds, v, dragLayerBounds[0], dragLayerBounds[1],
                hasSplashScreen, floatingView.isDifferentFromAppIcon());
        int left = prop.cropCenterXStart - prop.cropWidthStart / 2;
        int top = prop.cropCenterYStart - prop.cropHeightStart / 2;
        int right = left + prop.cropWidthStart;
        int bottom = top + prop.cropHeightStart;
        // Set the crop here so we can calculate the corner radius below.
        crop.set(left, top, right, bottom);

        RectF floatingIconBounds = new RectF();
        RectF tmpRectF = new RectF();
        Point tmpPos = new Point();

        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setDuration(APP_LAUNCH_DURATION);
        appAnimator.setInterpolator(LINEAR);
        appAnimator.addListener(floatingView);
        appAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                LauncherTaskbarUIController taskbarController = mLauncher.getTaskbarUIController();
                if (taskbarController != null && taskbarController.shouldShowEduOnAppLaunch()) {
                    // LAUNCHER_TASKBAR_EDUCATION_SHOWING is set to true here, when the education
                    // flow is about to start, to avoid a race condition with other components
                    // that would show something else to the user as soon as the app is opened.
                    Settings.Secure.putInt(mLauncher.getContentResolver(),
                            LAUNCHER_TASKBAR_EDUCATION_SHOWING, 1);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (v instanceof BubbleTextView) {
                    ((BubbleTextView) v).setStayPressed(false);
                }
                LauncherTaskbarUIController taskbarController = mLauncher.getTaskbarUIController();
                if (taskbarController != null) {
                    taskbarController.showEduOnAppLaunch();
                }
                openingTargets.release();
            }
        });

        final float initialWindowRadius = supportsRoundedCornersOnWindows(mLauncher.getResources())
                ? Math.max(crop.width(), crop.height()) / 2f
                : 0f;
        final float finalWindowRadius = mDeviceProfile.isMultiWindowMode
                ? 0 : getWindowCornerRadius(mLauncher);
        final float finalShadowRadius = appTargetsAreTranslucent ? 0 : mMaxShadowRadius;

        MultiValueUpdateListener listener = new MultiValueUpdateListener() {
            FloatProp mDx = new FloatProp(0, prop.dX, mOpeningXInterpolator);
            FloatProp mDy = new FloatProp(0, prop.dY, mOpeningInterpolator);

            FloatProp mIconScaleToFitScreen = new FloatProp(prop.initialAppIconScale,
                    prop.finalAppIconScale, mOpeningInterpolator);
            FloatProp mIconAlpha = new FloatProp(prop.iconAlphaStart, 0f,
                    clampToDuration(LINEAR, APP_LAUNCH_ALPHA_START_DELAY, APP_LAUNCH_ALPHA_DURATION,
                            APP_LAUNCH_DURATION));

            FloatProp mWindowRadius = new FloatProp(initialWindowRadius, finalWindowRadius,
                    mOpeningInterpolator);
            FloatProp mShadowRadius = new FloatProp(0, finalShadowRadius,
                    mOpeningInterpolator);

            FloatProp mCropRectCenterX = new FloatProp(prop.cropCenterXStart, prop.cropCenterXEnd,
                    mOpeningInterpolator);
            FloatProp mCropRectCenterY = new FloatProp(prop.cropCenterYStart, prop.cropCenterYEnd,
                    mOpeningInterpolator);
            FloatProp mCropRectWidth = new FloatProp(prop.cropWidthStart, prop.cropWidthEnd,
                    mOpeningInterpolator);
            FloatProp mCropRectHeight = new FloatProp(prop.cropHeightStart, prop.cropHeightEnd,
                    mOpeningInterpolator);

            FloatProp mNavFadeOut = new FloatProp(1f, 0f, clampToDuration(
                    NAV_FADE_OUT_INTERPOLATOR, 0, ANIMATION_NAV_FADE_OUT_DURATION,
                    APP_LAUNCH_DURATION));
            FloatProp mNavFadeIn = new FloatProp(0f, 1f, clampToDuration(
                    NAV_FADE_IN_INTERPOLATOR, ANIMATION_DELAY_NAV_FADE_IN,
                    ANIMATION_NAV_FADE_IN_DURATION, APP_LAUNCH_DURATION));

            @Override
            public void onUpdate(float percent, boolean initOnly) {
                // Calculate the size of the scaled icon.
                float iconWidth = launcherIconBounds.width() * mIconScaleToFitScreen.value;
                float iconHeight = launcherIconBounds.height() * mIconScaleToFitScreen.value;

                int left = (int) (mCropRectCenterX.value - mCropRectWidth.value / 2);
                int top = (int) (mCropRectCenterY.value - mCropRectHeight.value / 2);
                int right = (int) (left + mCropRectWidth.value);
                int bottom = (int) (top + mCropRectHeight.value);
                crop.set(left, top, right, bottom);

                final int windowCropWidth = crop.width();
                final int windowCropHeight = crop.height();
                if (rotationChange != 0) {
                    Utilities.rotateBounds(crop, mDeviceProfile.widthPx,
                            mDeviceProfile.heightPx, rotationChange);
                }

                // Scale the size of the icon to match the size of the window crop.
                float scaleX = iconWidth / windowCropWidth;
                float scaleY = iconHeight / windowCropHeight;
                float scale = Math.min(1f, Math.max(scaleX, scaleY));

                float scaledCropWidth = windowCropWidth * scale;
                float scaledCropHeight = windowCropHeight * scale;
                float offsetX = (scaledCropWidth - iconWidth) / 2;
                float offsetY = (scaledCropHeight - iconHeight) / 2;

                // Calculate the window position to match the icon position.
                tmpRectF.set(launcherIconBounds);
                tmpRectF.offset(dragLayerBounds[0], dragLayerBounds[1]);
                tmpRectF.offset(mDx.value, mDy.value);
                Utilities.scaleRectFAboutCenter(tmpRectF, mIconScaleToFitScreen.value);
                float windowTransX0 = tmpRectF.left - offsetX - crop.left * scale;
                float windowTransY0 = tmpRectF.top - offsetY - crop.top * scale;

                // Calculate the icon position.
                floatingIconBounds.set(launcherIconBounds);
                floatingIconBounds.offset(mDx.value, mDy.value);
                Utilities.scaleRectFAboutCenter(floatingIconBounds, mIconScaleToFitScreen.value);
                floatingIconBounds.left -= offsetX;
                floatingIconBounds.top -= offsetY;
                floatingIconBounds.right += offsetX;
                floatingIconBounds.bottom += offsetY;

                if (initOnly) {
                    // For the init pass, we want full alpha since the window is not yet ready.
                    floatingView.update(1f, floatingIconBounds, percent, 0f,
                            mWindowRadius.value * scale, true /* isOpening */);
                    return;
                }

                SurfaceTransaction transaction = new SurfaceTransaction();

                for (int i = appTargets.length - 1; i >= 0; i--) {
                    RemoteAnimationTarget target = appTargets[i];
                    SurfaceProperties builder = transaction.forSurface(target.leash);

                    if (target.mode == MODE_OPENING) {
                        matrix.setScale(scale, scale);
                        if (rotationChange == 1) {
                            matrix.postTranslate(windowTransY0,
                                    mDeviceProfile.widthPx - (windowTransX0 + scaledCropWidth));
                        } else if (rotationChange == 2) {
                            matrix.postTranslate(
                                    mDeviceProfile.widthPx - (windowTransX0 + scaledCropWidth),
                                    mDeviceProfile.heightPx - (windowTransY0 + scaledCropHeight));
                        } else if (rotationChange == 3) {
                            matrix.postTranslate(
                                    mDeviceProfile.heightPx - (windowTransY0 + scaledCropHeight),
                                    windowTransX0);
                        } else {
                            matrix.postTranslate(windowTransX0, windowTransY0);
                        }

                        floatingView.update(mIconAlpha.value, floatingIconBounds, percent, 0f,
                                mWindowRadius.value * scale, true /* isOpening */);
                        builder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(1f - mIconAlpha.value)
                                .setCornerRadius(mWindowRadius.value)
                                .setShadowRadius(mShadowRadius.value);
                    } else if (target.mode == MODE_CLOSING) {
                        if (target.localBounds != null) {
                            tmpPos.set(target.localBounds.left, target.localBounds.top);
                        } else {
                            tmpPos.set(target.position.x, target.position.y);
                        }
                        final Rect crop = new Rect(target.screenSpaceBounds);
                        crop.offsetTo(0, 0);

                        if ((rotationChange % 2) == 1) {
                            int tmp = crop.right;
                            crop.right = crop.bottom;
                            crop.bottom = tmp;
                            tmp = tmpPos.x;
                            tmpPos.x = tmpPos.y;
                            tmpPos.y = tmp;
                        }
                        matrix.setTranslate(tmpPos.x, tmpPos.y);
                        builder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(1f);
                    }
                }

                if (navBarTarget != null) {
                    SurfaceProperties navBuilder =
                            transaction.forSurface(navBarTarget.leash);
                    if (mNavFadeIn.value > mNavFadeIn.getStartValue()) {
                        matrix.setScale(scale, scale);
                        matrix.postTranslate(windowTransX0, windowTransY0);
                        navBuilder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(mNavFadeIn.value);
                    } else {
                        navBuilder.setAlpha(mNavFadeOut.value);
                    }
                }
                surfaceApplier.scheduleApply(transaction);
            }
        };
        appAnimator.addUpdateListener(listener);
        // Since we added a start delay, call update here to init the FloatingIconView properly.
        listener.onUpdate(0, true /* initOnly */);

        // If app targets are translucent, do not animate the background as it causes a visible
        // flicker when it resets itself at the end of its animation.
        if (appTargetsAreTranslucent || !launcherClosing) {
            animatorSet.play(appAnimator);
        } else {
            animatorSet.playTogether(appAnimator, getBackgroundAnimator());
        }
        return animatorSet;
    }

    private Animator getOpeningWindowAnimatorsForWidget(LauncherAppWidgetHostView v,
            RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpaperTargets,
            RemoteAnimationTarget[] nonAppTargets, boolean launcherClosing) {
        Rect windowTargetBounds = getWindowTargetBounds(appTargets, getRotationChange(appTargets));
        boolean appTargetsAreTranslucent = areAllTargetsTranslucent(appTargets);

        final RectF widgetBackgroundBounds = new RectF();
        final Rect appWindowCrop = new Rect();
        final Matrix matrix = new Matrix();
        RemoteAnimationTargets openingTargets = new RemoteAnimationTargets(appTargets,
                wallpaperTargets, nonAppTargets, MODE_OPENING);

        RemoteAnimationTarget openingTarget = openingTargets.getFirstAppTarget();
        int fallbackBackgroundColor = 0;
        if (openingTarget != null && ENABLE_SHELL_STARTING_SURFACE) {
            fallbackBackgroundColor = mTaskStartParams.containsKey(openingTarget.taskId)
                    ? mTaskStartParams.get(openingTarget.taskId).second : 0;
            mTaskStartParams.remove(openingTarget.taskId);
        }
        if (fallbackBackgroundColor == 0) {
            fallbackBackgroundColor =
                    FloatingWidgetView.getDefaultBackgroundColor(mLauncher, openingTarget);
        }

        final float finalWindowRadius = mDeviceProfile.isMultiWindowMode
                ? 0 : getWindowCornerRadius(mLauncher);
        final FloatingWidgetView floatingView = FloatingWidgetView.getFloatingWidgetView(mLauncher,
                v, widgetBackgroundBounds,
                new Size(windowTargetBounds.width(), windowTargetBounds.height()),
                finalWindowRadius, appTargetsAreTranslucent, fallbackBackgroundColor);
        final float initialWindowRadius = supportsRoundedCornersOnWindows(mLauncher.getResources())
                ? floatingView.getInitialCornerRadius() : 0;

        SurfaceTransactionApplier surfaceApplier = new SurfaceTransactionApplier(floatingView);
        openingTargets.addReleaseCheck(surfaceApplier);

        RemoteAnimationTarget navBarTarget = openingTargets.getNavBarRemoteAnimationTarget();

        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setDuration(APP_LAUNCH_DURATION);
        appAnimator.setInterpolator(LINEAR);
        appAnimator.addListener(floatingView);
        appAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openingTargets.release();
            }
        });
        floatingView.setFastFinishRunnable(animatorSet::end);

        appAnimator.addUpdateListener(new MultiValueUpdateListener() {
            float mAppWindowScale = 1;
            final FloatProp mWidgetForegroundAlpha = new FloatProp(1, 0, clampToDuration(
                    LINEAR, 0, WIDGET_CROSSFADE_DURATION_MILLIS / 2, APP_LAUNCH_DURATION));

            final FloatProp mWidgetFallbackBackgroundAlpha = new FloatProp(0, 1,
                    clampToDuration(LINEAR, 0, 75, APP_LAUNCH_DURATION));
            final FloatProp mPreviewAlpha = new FloatProp(0, 1, clampToDuration(
                    LINEAR,
                    WIDGET_CROSSFADE_DURATION_MILLIS / 2 /* delay */,
                    WIDGET_CROSSFADE_DURATION_MILLIS / 2 /* duration */,
                    APP_LAUNCH_DURATION));
            final FloatProp mWindowRadius = new FloatProp(initialWindowRadius, finalWindowRadius,
                    mOpeningInterpolator);
            final FloatProp mCornerRadiusProgress = new FloatProp(0, 1, mOpeningInterpolator);

            // Window & widget background positioning bounds
            final FloatProp mDx = new FloatProp(widgetBackgroundBounds.centerX(),
                    windowTargetBounds.centerX(), mOpeningXInterpolator);
            final FloatProp mDy = new FloatProp(widgetBackgroundBounds.centerY(),
                    windowTargetBounds.centerY(), mOpeningInterpolator);
            final FloatProp mWidth = new FloatProp(widgetBackgroundBounds.width(),
                    windowTargetBounds.width(), mOpeningInterpolator);
            final FloatProp mHeight = new FloatProp(widgetBackgroundBounds.height(),
                    windowTargetBounds.height(), mOpeningInterpolator);

            final FloatProp mNavFadeOut = new FloatProp(1f, 0f, clampToDuration(
                    NAV_FADE_OUT_INTERPOLATOR, 0, ANIMATION_NAV_FADE_OUT_DURATION,
                    APP_LAUNCH_DURATION));
            final FloatProp mNavFadeIn = new FloatProp(0f, 1f, clampToDuration(
                    NAV_FADE_IN_INTERPOLATOR, ANIMATION_DELAY_NAV_FADE_IN,
                    ANIMATION_NAV_FADE_IN_DURATION, APP_LAUNCH_DURATION));

            @Override
            public void onUpdate(float percent, boolean initOnly) {
                widgetBackgroundBounds.set(mDx.value - mWidth.value / 2f,
                        mDy.value - mHeight.value / 2f, mDx.value + mWidth.value / 2f,
                        mDy.value + mHeight.value / 2f);
                // Set app window scaling factor to match widget background width
                mAppWindowScale = widgetBackgroundBounds.width() / windowTargetBounds.width();
                // Crop scaled app window to match widget
                appWindowCrop.set(0 /* left */, 0 /* top */,
                        windowTargetBounds.width() /* right */,
                        Math.round(widgetBackgroundBounds.height() / mAppWindowScale) /* bottom */);
                matrix.setTranslate(widgetBackgroundBounds.left, widgetBackgroundBounds.top);
                matrix.postScale(mAppWindowScale, mAppWindowScale, widgetBackgroundBounds.left,
                        widgetBackgroundBounds.top);

                SurfaceTransaction transaction = new SurfaceTransaction();
                float floatingViewAlpha = appTargetsAreTranslucent ? 1 - mPreviewAlpha.value : 1;
                for (int i = appTargets.length - 1; i >= 0; i--) {
                    RemoteAnimationTarget target = appTargets[i];
                    SurfaceProperties builder = transaction.forSurface(target.leash);
                    if (target.mode == MODE_OPENING) {
                        floatingView.update(widgetBackgroundBounds, floatingViewAlpha,
                                mWidgetForegroundAlpha.value, mWidgetFallbackBackgroundAlpha.value,
                                mCornerRadiusProgress.value);
                        builder.setMatrix(matrix)
                                .setWindowCrop(appWindowCrop)
                                .setAlpha(mPreviewAlpha.value)
                                .setCornerRadius(mWindowRadius.value / mAppWindowScale);
                    }
                }

                if (navBarTarget != null) {
                    SurfaceProperties navBuilder = transaction.forSurface(navBarTarget.leash);
                    if (mNavFadeIn.value > mNavFadeIn.getStartValue()) {
                        navBuilder.setMatrix(matrix)
                                .setWindowCrop(appWindowCrop)
                                .setAlpha(mNavFadeIn.value);
                    } else {
                        navBuilder.setAlpha(mNavFadeOut.value);
                    }
                }
                surfaceApplier.scheduleApply(transaction);
            }
        });

        // If app targets are translucent, do not animate the background as it causes a visible
        // flicker when it resets itself at the end of its animation.
        if (appTargetsAreTranslucent || !launcherClosing) {
            animatorSet.play(appAnimator);
        } else {
            animatorSet.playTogether(appAnimator, getBackgroundAnimator());
        }
        return animatorSet;
    }

    /**
     * Returns animator that controls depth/blur of the background.
     */
    private ObjectAnimator getBackgroundAnimator() {
        // When launching an app from overview that doesn't map to a task, we still want to just
        // blur the wallpaper instead of the launcher surface as well
        boolean allowBlurringLauncher = mLauncher.getStateManager().getState() != OVERVIEW
                && BlurUtils.supportsBlursOnWindows();

        LaunchDepthController depthController = new LaunchDepthController(mLauncher);
        ObjectAnimator backgroundRadiusAnim = ObjectAnimator.ofFloat(depthController.stateDepth,
                        MULTI_PROPERTY_VALUE, BACKGROUND_APP.getDepth(mLauncher))
                        .setDuration(APP_LAUNCH_DURATION);

        if (allowBlurringLauncher) {
            // Create a temporary effect layer, that lives on top of launcher, so we can apply
            // the blur to it. The EffectLayer will be fullscreen, which will help with caching
            // optimizations on the SurfaceFlinger side:
            // - Results would be able to be cached as a texture
            // - There won't be texture allocation overhead, because EffectLayers don't have
            //   buffers
            ViewRootImpl viewRootImpl = mLauncher.getDragLayer().getViewRootImpl();
            SurfaceControl parent = viewRootImpl != null
                    ? viewRootImpl.getSurfaceControl()
                    : null;
            SurfaceControl dimLayer = new SurfaceControl.Builder()
                    .setName("Blur layer")
                    .setParent(parent)
                    .setOpaque(false)
                    .setHidden(false)
                    .setEffectLayer()
                    .build();

            backgroundRadiusAnim.addListener(AnimatorListeners.forEndCallback(() ->
                    new SurfaceControl.Transaction().remove(dimLayer).apply()));
        }

        backgroundRadiusAnim.addListener(
                AnimatorListeners.forEndCallback(() -> {
                    // reset the depth to match the main depth controller's depth
                    depthController.stateDepth
                            .setValue(mLauncher.getDepthController().stateDepth.getValue());
                    depthController.dispose();
                }));

        return backgroundRadiusAnim;
    }

    /**
     * Registers remote animations used when closing apps to home screen.
     */
    public void registerRemoteAnimations() {
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        RemoteAnimationDefinition definition = new RemoteAnimationDefinition();
        addRemoteAnimations(definition);
        mLauncher.registerRemoteAnimations(definition);
    }

    /**
     * Adds remote animations to a {@link RemoteAnimationDefinition}. May be overridden to add
     * additional animations.
     */
    private void addRemoteAnimations(RemoteAnimationDefinition definition) {
        mWallpaperOpenRunner = createWallpaperOpenRunner(false /* fromUnlock */);
        definition.addRemoteAnimation(WindowManager.TRANSIT_OLD_WALLPAPER_OPEN,
                WindowConfiguration.ACTIVITY_TYPE_STANDARD,
                new RemoteAnimationAdapter(
                        new LauncherAnimationRunner(mHandler, mWallpaperOpenRunner,
                                false /* startAtFrontOfQueue */),
                        CLOSING_TRANSITION_DURATION_MS, 0 /* statusBarTransitionDelay */));

        if (KEYGUARD_ANIMATION.get()) {
            mKeyguardGoingAwayRunner = createWallpaperOpenRunner(true /* fromUnlock */);
            definition.addRemoteAnimation(
                    WindowManager.TRANSIT_OLD_KEYGUARD_GOING_AWAY_ON_WALLPAPER,
                    new RemoteAnimationAdapter(
                            new LauncherAnimationRunner(
                                    mHandler, mKeyguardGoingAwayRunner,
                                    true /* startAtFrontOfQueue */),
                            CLOSING_TRANSITION_DURATION_MS, 0 /* statusBarTransitionDelay */));
        }
    }

    /**
     * Registers remote animations used when closing apps to home screen.
     */
    public void registerRemoteTransitions() {
        if (ENABLE_SHELL_TRANSITIONS) {
            SystemUiProxy.INSTANCE.get(mLauncher).shareTransactionQueue();
        }
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }

        mWallpaperOpenTransitionRunner = createWallpaperOpenRunner(false /* fromUnlock */);
        mLauncherOpenTransition = new RemoteTransition(
                new LauncherAnimationRunner(mHandler, mWallpaperOpenTransitionRunner,
                        false /* startAtFrontOfQueue */).toRemoteTransition(),
                mLauncher.getIApplicationThread(), "QuickstepLaunchHome");

        TransitionFilter homeCheck = new TransitionFilter();
        // No need to handle the transition that also dismisses keyguard.
        homeCheck.mNotFlags = TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
        homeCheck.mRequirements =
                new TransitionFilter.Requirement[]{new TransitionFilter.Requirement(),
                        new TransitionFilter.Requirement()};
        homeCheck.mRequirements[0].mActivityType = ACTIVITY_TYPE_HOME;
        homeCheck.mRequirements[0].mTopActivity = mLauncher.getComponentName();
        homeCheck.mRequirements[0].mModes = new int[]{TRANSIT_OPEN, TRANSIT_TO_FRONT};
        homeCheck.mRequirements[0].mOrder = CONTAINER_ORDER_TOP;
        homeCheck.mRequirements[1].mActivityType = ACTIVITY_TYPE_STANDARD;
        homeCheck.mRequirements[1].mModes = new int[]{TRANSIT_CLOSE, TRANSIT_TO_BACK};
        SystemUiProxy.INSTANCE.get(mLauncher)
                .registerRemoteTransition(mLauncherOpenTransition, homeCheck);
        if (mBackAnimationController != null) {
            mBackAnimationController.registerComponentCallbacks();
            mBackAnimationController.registerBackCallbacks(mHandler);
        }
    }

    public void onActivityDestroyed() {
        unregisterRemoteAnimations();
        unregisterRemoteTransitions();
        mLauncher.removeOnDeviceProfileChangeListener(this);
        SystemUiProxy.INSTANCE.get(mLauncher).setStartingWindowListener(null);
        ORDERED_BG_EXECUTOR.execute(() -> mLauncher.getContentResolver()
                .unregisterContentObserver(mAnimationRemovalObserver));
    }

    private void unregisterRemoteAnimations() {
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        mLauncher.unregisterRemoteAnimations();

        // Also clear strong references to the runners registered with the remote animation
        // definition so we don't have to wait for the system gc
        mWallpaperOpenRunner = null;
        mAppLaunchRunner = null;
        mKeyguardGoingAwayRunner = null;
    }

    protected void unregisterRemoteTransitions() {
        if (ENABLE_SHELL_TRANSITIONS) {
            SystemUiProxy.INSTANCE.get(mLauncher).unshareTransactionQueue();
        }
        if (SEPARATE_RECENTS_ACTIVITY.get()) {
            return;
        }
        if (mLauncherOpenTransition == null) return;
        SystemUiProxy.INSTANCE.get(mLauncher).unregisterRemoteTransition(
                mLauncherOpenTransition);
        mLauncherOpenTransition = null;
        mWallpaperOpenTransitionRunner = null;
        if (mBackAnimationController != null) {
            mBackAnimationController.unregisterBackCallbacks();
            mBackAnimationController.unregisterComponentCallbacks();
            mBackAnimationController = null;
        }
    }

    private void checkAndMonitorIfAnimationsAreEnabled() {
        ORDERED_BG_EXECUTOR.execute(() -> {
            mAnimationRemovalObserver.onChange(true);
            mLauncher.getContentResolver().registerContentObserver(Global.getUriFor(
                    Global.ANIMATOR_DURATION_SCALE), false, mAnimationRemovalObserver);
            mLauncher.getContentResolver().registerContentObserver(Global.getUriFor(
                    Global.TRANSITION_ANIMATION_SCALE), false, mAnimationRemovalObserver);

        });
    }

    private boolean launcherIsATargetWithMode(RemoteAnimationTarget[] targets, int mode) {
        for (RemoteAnimationTarget target : targets) {
            if (target.mode == mode && target.taskInfo != null
                    // Compare component name instead of task-id because transitions will promote
                    // the target up to the root task while getTaskId returns the leaf.
                    && target.taskInfo.topActivity != null
                    && target.taskInfo.topActivity.equals(mLauncher.getComponentName())) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldPlayFallbackClosingAnimation(RemoteAnimationTarget[] targets) {
        int numTargets = 0;
        for (RemoteAnimationTarget target : targets) {
            if (target.mode == MODE_CLOSING) {
                numTargets++;
                if (numTargets > 1 || target.windowConfiguration.getWindowingMode()
                        == WINDOWING_MODE_MULTI_WINDOW) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @return Runner that plays when user goes to Launcher
     * ie. pressing home, swiping up from nav bar.
     */
    RemoteAnimationFactory createWallpaperOpenRunner(boolean fromUnlock) {
        return new WallpaperOpenLauncherAnimationRunner(fromUnlock);
    }

    /**
     * Animator that controls the transformations of the windows when unlocking the device.
     */
    private Animator getUnlockWindowAnimator(RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpaperTargets) {
        SurfaceTransactionApplier surfaceApplier = new SurfaceTransactionApplier(mDragLayer);
        ValueAnimator unlockAnimator = ValueAnimator.ofFloat(0, 1);
        unlockAnimator.setDuration(CLOSING_TRANSITION_DURATION_MS);
        float cornerRadius = mDeviceProfile.isMultiWindowMode ? 0 :
                QuickStepContract.getWindowCornerRadius(mLauncher);
        unlockAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                SurfaceTransaction transaction = new SurfaceTransaction();
                for (int i = appTargets.length - 1; i >= 0; i--) {
                    RemoteAnimationTarget target = appTargets[i];
                    transaction.forSurface(target.leash)
                            .setAlpha(1f)
                            .setWindowCrop(target.screenSpaceBounds)
                            .setCornerRadius(cornerRadius);
                }
                surfaceApplier.scheduleApply(transaction);
            }
        });
        return unlockAnimator;
    }

    private static int getRotationChange(RemoteAnimationTarget[] appTargets) {
        int rotationChange = 0;
        for (RemoteAnimationTarget target : appTargets) {
            if (Math.abs(target.rotationChange) > Math.abs(rotationChange)) {
                rotationChange = target.rotationChange;
            }
        }
        return rotationChange;
    }

    /**
     * Returns view on launcher that corresponds to the closing app in the list of app targets
     */
    public @Nullable View findLauncherView(RemoteAnimationTarget[] appTargets) {
        for (RemoteAnimationTarget appTarget : appTargets) {
            if (appTarget.mode == MODE_CLOSING) {
                View launcherView = findLauncherView(appTarget);
                if (launcherView != null) {
                    return launcherView;
                }
            }
        }
        return null;
    }

    /**
     * Returns view on launcher that corresponds to the {@param runningTaskTarget}.
     */
    private @Nullable View findLauncherView(RemoteAnimationTarget runningTaskTarget) {
        if (runningTaskTarget == null || runningTaskTarget.taskInfo == null) {
            return null;
        }

        final ComponentName[] taskInfoActivities = new ComponentName[]{
                runningTaskTarget.taskInfo.baseActivity,
                runningTaskTarget.taskInfo.origActivity,
                runningTaskTarget.taskInfo.realActivity,
                runningTaskTarget.taskInfo.topActivity};

        String packageName = null;
        for (ComponentName component : taskInfoActivities) {
            if (component != null && component.getPackageName() != null) {
                packageName = component.getPackageName();
                break;
            }
        }

        if (packageName == null) {
            return null;
        }

        // Find the associated item info for the launch cookie (if available), note that predicted
        // apps actually have an id of -1, so use another default id here
        final ArrayList<IBinder> launchCookies = runningTaskTarget.taskInfo.launchCookies == null
                ? new ArrayList<>()
                : runningTaskTarget.taskInfo.launchCookies;

        int launchCookieItemId = NO_MATCHING_ID;
        for (IBinder cookie : launchCookies) {
            Integer itemId = ObjectWrapper.unwrap(cookie);
            if (itemId != null) {
                launchCookieItemId = itemId;
                break;
            }
        }

        return mLauncher.getFirstMatchForAppClose(launchCookieItemId, packageName,
                UserHandle.of(runningTaskTarget.taskInfo.userId), true /* supportsAllAppsState */);
    }

    private @NonNull RectF getDefaultWindowTargetRect() {
        RecentsView recentsView = mLauncher.getOverviewPanel();
        PagedOrientationHandler orientationHandler = recentsView.getPagedOrientationHandler();
        DeviceProfile dp = mLauncher.getDeviceProfile();
        final int halfIconSize = dp.iconSizePx / 2;
        float primaryDimension = orientationHandler
                .getPrimaryValue(dp.availableWidthPx, dp.availableHeightPx);
        float secondaryDimension = orientationHandler
                .getSecondaryValue(dp.availableWidthPx, dp.availableHeightPx);
        final float targetX = primaryDimension / 2f;
        final float targetY = secondaryDimension - dp.hotseatBarSizePx;
        return new RectF(targetX - halfIconSize, targetY - halfIconSize,
                targetX + halfIconSize, targetY + halfIconSize);
    }

    /**
     * Closing animator that animates the window into its final location on the workspace.
     */
    protected RectFSpringAnim getClosingWindowAnimators(AnimatorSet animation,
            RemoteAnimationTarget[] targets, View launcherView, PointF velocityPxPerS,
            RectF closingWindowStartRectF, float startWindowCornerRadius) {
        FloatingIconView floatingIconView = null;
        FloatingWidgetView floatingWidget = null;
        RectF targetRect = new RectF();

        RemoteAnimationTarget runningTaskTarget = null;
        boolean isTransluscent = false;
        for (RemoteAnimationTarget target : targets) {
            if (target.mode == MODE_CLOSING) {
                runningTaskTarget = target;
                isTransluscent = runningTaskTarget.isTranslucent;
                break;
            }
        }

        // Get floating view and target rect.
        boolean isInHotseat = false;
        if (launcherView instanceof LauncherAppWidgetHostView) {
            Size windowSize = new Size(mDeviceProfile.availableWidthPx,
                    mDeviceProfile.availableHeightPx);
            int fallbackBackgroundColor =
                    FloatingWidgetView.getDefaultBackgroundColor(mLauncher, runningTaskTarget);
            floatingWidget = FloatingWidgetView.getFloatingWidgetView(mLauncher,
                    (LauncherAppWidgetHostView) launcherView, targetRect, windowSize,
                    mDeviceProfile.isMultiWindowMode ? 0 : getWindowCornerRadius(mLauncher),
                    isTransluscent, fallbackBackgroundColor);
        } else if (launcherView != null && mAreAnimationsEnabled) {
            floatingIconView = getFloatingIconView(mLauncher, launcherView, null,
                    mLauncher.getTaskbarUIController() == null
                            ? null
                            : mLauncher.getTaskbarUIController().findMatchingView(launcherView),
                    true /* hideOriginal */, targetRect, false /* isOpening */);
            isInHotseat = launcherView.getTag() instanceof ItemInfo
                    && ((ItemInfo) launcherView.getTag()).isInHotseat();
        } else {
            targetRect.set(getDefaultWindowTargetRect());
        }

        boolean useTaskbarHotseatParams = mDeviceProfile.isTaskbarPresent && isInHotseat;
        RectFSpringAnim anim = new RectFSpringAnim(useTaskbarHotseatParams
                ? new TaskbarHotseatSpringConfig(mLauncher, closingWindowStartRectF, targetRect)
                : new DefaultSpringConfig(mLauncher, mDeviceProfile, closingWindowStartRectF,
                        targetRect));

        // Hook up floating views to the closing window animators.
        // note the coordinate of closingWindowStartRect is based on launcher
        Rect closingWindowStartRect = new Rect();
        closingWindowStartRectF.round(closingWindowStartRect);
        Rect closingWindowOriginalRect =
                new Rect(0, 0, mDeviceProfile.widthPx, mDeviceProfile.heightPx);
        if (floatingIconView != null) {
            anim.addAnimatorListener(floatingIconView);
            floatingIconView.setOnTargetChangeListener(anim::onTargetPositionChanged);
            floatingIconView.setFastFinishRunnable(anim::end);
            FloatingIconView finalFloatingIconView = floatingIconView;

            // We want the window alpha to be 0 once this threshold is met, so that the
            // FolderIconView can be seen morphing into the icon shape.
            final float windowAlphaThreshold = 1f - SHAPE_PROGRESS_DURATION;

            RectFSpringAnim.OnUpdateListener runner = new SpringAnimRunner(targets, targetRect,
                    closingWindowStartRect, closingWindowOriginalRect, startWindowCornerRadius) {
                @Override
                public void onUpdate(RectF currentRectF, float progress) {
                    finalFloatingIconView.update(1f, currentRectF, progress, windowAlphaThreshold,
                            getCornerRadius(progress), false);

                    super.onUpdate(currentRectF, progress);
                }
            };
            anim.addOnUpdateListener(runner);
        } else if (floatingWidget != null) {
            anim.addAnimatorListener(floatingWidget);
            floatingWidget.setOnTargetChangeListener(anim::onTargetPositionChanged);
            floatingWidget.setFastFinishRunnable(anim::end);

            final float floatingWidgetAlpha = isTransluscent ? 0 : 1;
            FloatingWidgetView finalFloatingWidget = floatingWidget;
            RectFSpringAnim.OnUpdateListener runner = new SpringAnimRunner(targets, targetRect,
                    closingWindowStartRect, closingWindowOriginalRect, startWindowCornerRadius) {
                @Override
                public void onUpdate(RectF currentRectF, float progress) {
                    final float fallbackBackgroundAlpha =
                            1 - mapBoundToRange(progress, 0.8f, 1, 0, 1, EXAGGERATED_EASE);
                    final float foregroundAlpha =
                            mapBoundToRange(progress, 0.5f, 1, 0, 1, EXAGGERATED_EASE);
                    finalFloatingWidget.update(currentRectF, floatingWidgetAlpha, foregroundAlpha,
                            fallbackBackgroundAlpha, 1 - progress);

                    super.onUpdate(currentRectF, progress);
                }
            };
            anim.addOnUpdateListener(runner);
        } else {
            // If no floating icon or widget is present, animate the to the default window
            // target rect.
            anim.addOnUpdateListener(new SpringAnimRunner(
                    targets, targetRect, closingWindowStartRect, closingWindowOriginalRect,
                    startWindowCornerRadius));
        }

        // Use a fixed velocity to start the animation.
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                anim.start(mLauncher, mDeviceProfile, velocityPxPerS);
            }
        });
        return anim;
    }

    /**
     * Closing window animator that moves the window down and offscreen.
     */
    private Animator getFallbackClosingWindowAnimators(RemoteAnimationTarget[] appTargets) {
        final int rotationChange = getRotationChange(appTargets);
        SurfaceTransactionApplier surfaceApplier = new SurfaceTransactionApplier(mDragLayer);
        Matrix matrix = new Matrix();
        Point tmpPos = new Point();
        Rect tmpRect = new Rect();
        ValueAnimator closingAnimator = ValueAnimator.ofFloat(0, 1);
        int duration = CLOSING_TRANSITION_DURATION_MS;
        float windowCornerRadius = mDeviceProfile.isMultiWindowMode
                ? 0 : getWindowCornerRadius(mLauncher);
        float startShadowRadius = areAllTargetsTranslucent(appTargets) ? 0 : mMaxShadowRadius;
        closingAnimator.setDuration(duration);
        closingAnimator.addUpdateListener(new MultiValueUpdateListener() {
            FloatProp mDy = new FloatProp(0, mClosingWindowTransY, DECELERATE_1_7);
            FloatProp mScale = new FloatProp(1f, 1f, DECELERATE_1_7);
            FloatProp mAlpha = new FloatProp(1f, 0f, clampToDuration(LINEAR, 25, 125, duration));
            FloatProp mShadowRadius = new FloatProp(startShadowRadius, 0, DECELERATE_1_7);

            @Override
            public void onUpdate(float percent, boolean initOnly) {
                SurfaceTransaction transaction = new SurfaceTransaction();
                for (int i = appTargets.length - 1; i >= 0; i--) {
                    RemoteAnimationTarget target = appTargets[i];
                    SurfaceProperties builder = transaction.forSurface(target.leash);

                    if (target.screenSpaceBounds != null) {
                        tmpPos.set(target.screenSpaceBounds.left, target.screenSpaceBounds.top);
                    } else {
                        tmpPos.set(target.position.x, target.position.y);
                    }

                    final Rect crop = new Rect(target.localBounds);
                    crop.offsetTo(0, 0);
                    if (target.mode == MODE_CLOSING) {
                        tmpRect.set(target.screenSpaceBounds);
                        if ((rotationChange % 2) != 0) {
                            final int right = crop.right;
                            crop.right = crop.bottom;
                            crop.bottom = right;
                        }
                        matrix.setScale(mScale.value, mScale.value,
                                tmpRect.centerX(),
                                tmpRect.centerY());
                        matrix.postTranslate(0, mDy.value);
                        matrix.postTranslate(tmpPos.x, tmpPos.y);
                        builder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(mAlpha.value)
                                .setCornerRadius(windowCornerRadius)
                                .setShadowRadius(mShadowRadius.value);
                    } else if (target.mode == MODE_OPENING) {
                        matrix.setTranslate(tmpPos.x, tmpPos.y);
                        builder.setMatrix(matrix)
                                .setWindowCrop(crop)
                                .setAlpha(1f);
                    }
                }
                surfaceApplier.scheduleApply(transaction);
            }
        });

        return closingAnimator;
    }

    private void addCujInstrumentation(Animator anim, int cuj) {
        anim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                mDragLayer.getViewTreeObserver().addOnDrawListener(
                        new ViewTreeObserver.OnDrawListener() {
                            boolean mHandled = false;

                            @Override
                            public void onDraw() {
                                if (mHandled) {
                                    return;
                                }
                                mHandled = true;

                                InteractionJankMonitorWrapper.begin(mDragLayer, cuj);

                                mDragLayer.post(() ->
                                        mDragLayer.getViewTreeObserver().removeOnDrawListener(
                                                this));
                            }
                        });
                super.onAnimationStart(animation);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                InteractionJankMonitorWrapper.cancel(cuj);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                InteractionJankMonitorWrapper.end(cuj);
            }
        });
    }

    /**
     * Creates the {@link RectFSpringAnim} and {@link AnimatorSet} required to animate
     * the transition.
     */
    public Pair<RectFSpringAnim, AnimatorSet> createWallpaperOpenAnimations(
            RemoteAnimationTarget[] appTargets,
            RemoteAnimationTarget[] wallpaperTargets,
            boolean fromUnlock,
            RectF startRect,
            float startWindowCornerRadius,
            boolean fromPredictiveBack) {
        AnimatorSet anim = new AnimatorSet();
        RectFSpringAnim rectFSpringAnim = null;

        final boolean launcherIsForceInvisibleOrOpening = mLauncher.isForceInvisible()
                || launcherIsATargetWithMode(appTargets, MODE_OPENING);

        View launcherView = findLauncherView(appTargets);
        boolean playFallBackAnimation = (launcherView == null
                && launcherIsForceInvisibleOrOpening)
                || mLauncher.getWorkspace().isOverlayShown()
                || shouldPlayFallbackClosingAnimation(appTargets);

        boolean playWorkspaceReveal = !fromPredictiveBack;
        boolean skipAllAppsScale = false;
        if (fromUnlock) {
            anim.play(getUnlockWindowAnimator(appTargets, wallpaperTargets));
        } else if (ENABLE_BACK_SWIPE_HOME_ANIMATION.get()
                && !playFallBackAnimation) {
            // Use a fixed velocity to start the animation.
            float velocityPxPerS = DynamicResource.provider(mLauncher)
                    .getDimension(R.dimen.unlock_staggered_velocity_dp_per_s);
            PointF velocity = new PointF(0, -velocityPxPerS);
            rectFSpringAnim = getClosingWindowAnimators(
                    anim, appTargets, launcherView, velocity, startRect,
                    startWindowCornerRadius);
            if (mLauncher.isInState(LauncherState.ALL_APPS)) {
                // Skip scaling all apps, otherwise FloatingIconView will get wrong
                // layout bounds.
                skipAllAppsScale = true;
            } else if (!fromPredictiveBack) {
                anim.play(new StaggeredWorkspaceAnim(mLauncher, velocity.y,
                        true /* animateOverviewScrim */, launcherView).getAnimators());

                if (!areAllTargetsTranslucent(appTargets)) {
                    anim.play(ObjectAnimator.ofFloat(mLauncher.getDepthController().stateDepth,
                            MULTI_PROPERTY_VALUE,
                            BACKGROUND_APP.getDepth(mLauncher), NORMAL.getDepth(mLauncher)));
                }

                // We play StaggeredWorkspaceAnim as a part of the closing window animation.
                playWorkspaceReveal = false;
            }
        } else {
            anim.play(getFallbackClosingWindowAnimators(appTargets));
        }

        // Normally, we run the launcher content animation when we are transitioning
        // home, but if home is already visible, then we don't want to animate the
        // contents of launcher unless we know that we are animating home as a result
        // of the home button press with quickstep, which will result in launcher being
        // started on touch down, prior to the animation home (and won't be in the
        // targets list because it is already visible). In that case, we force
        // invisibility on touch down, and only reset it after the animation to home
        // is initialized.
        if (launcherIsForceInvisibleOrOpening || fromPredictiveBack) {
            addCujInstrumentation(anim, playFallBackAnimation
                    ? Cuj.CUJ_LAUNCHER_APP_CLOSE_TO_HOME_FALLBACK
                    : Cuj.CUJ_LAUNCHER_APP_CLOSE_TO_HOME);

            AnimatorListenerAdapter endListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    AccessibilityManagerCompat.sendTestProtocolEventToTest(
                            mLauncher, WALLPAPER_OPEN_ANIMATION_FINISHED_MESSAGE);
                }
            };

            if (fromPredictiveBack && rectFSpringAnim != null) {
                rectFSpringAnim.addAnimatorListener(endListener);
            } else {
                anim.addListener(endListener);
            }

            // Only register the content animation for cancellation when state changes
            mLauncher.getStateManager().setCurrentAnimation(anim);

            if (mLauncher.isInState(LauncherState.ALL_APPS) && !fromPredictiveBack) {
                Pair<AnimatorSet, Runnable> contentAnimator =
                        getLauncherContentAnimator(false, LAUNCHER_RESUME_START_DELAY,
                                skipAllAppsScale);
                anim.play(contentAnimator.first);
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        contentAnimator.second.run();
                    }
                });
            } else if (playWorkspaceReveal) {
                    anim.play(new WorkspaceRevealAnim(mLauncher, false).getAnimators());
            }
        }

        return new Pair(rectFSpringAnim, anim);
    }

    public static int getTaskbarToHomeDuration() {
        if (enableScalingRevealHomeAnimation()) {
            return TASKBAR_TO_HOME_DURATION_SLOW;
        } else {
            return TASKBAR_TO_HOME_DURATION_FAST;
        }
    }

    /**
     * Remote animation runner for animation from the app to Launcher, including recents.
     */
    protected class WallpaperOpenLauncherAnimationRunner implements RemoteAnimationFactory {

        private final boolean mFromUnlock;

        public WallpaperOpenLauncherAnimationRunner(boolean fromUnlock) {
            mFromUnlock = fromUnlock;
        }

        @Override
        public void onAnimationStart(int transit,
                RemoteAnimationTarget[] appTargets,
                RemoteAnimationTarget[] wallpaperTargets,
                RemoteAnimationTarget[] nonAppTargets,
                LauncherAnimationRunner.AnimationResult result) {
            if (mLauncher.isDestroyed()) {
                AnimatorSet anim = new AnimatorSet();
                anim.play(getFallbackClosingWindowAnimators(appTargets));
                result.setAnimation(anim, mLauncher.getApplicationContext());
                return;
            }

            if (mLauncher.hasSomeInvisibleFlag(PENDING_INVISIBLE_BY_WALLPAPER_ANIMATION)) {
                mLauncher.addForceInvisibleFlag(INVISIBLE_BY_PENDING_FLAGS);
                mLauncher.getStateManager().moveToRestState();
            }

            RectF windowTargetBounds =
                    new RectF(getWindowTargetBounds(appTargets, getRotationChange(appTargets)));

            final RectF resolveRectF = new RectF(windowTargetBounds);
            for (RemoteAnimationTarget t : appTargets) {
                if (t.mode == MODE_CLOSING) {
                    transferRectToTargetCoordinate(
                            t, windowTargetBounds, true, resolveRectF);
                    break;
                }
            }

            Pair<RectFSpringAnim, AnimatorSet> pair = createWallpaperOpenAnimations(
                    appTargets, wallpaperTargets, mFromUnlock, resolveRectF,
                    QuickStepContract.getWindowCornerRadius(mLauncher),
                    false /* fromPredictiveBack */);

            TaskViewUtils.createSplitAuxiliarySurfacesAnimator(nonAppTargets, false, null);
            mLauncher.clearForceInvisibleFlag(INVISIBLE_ALL);
            result.setAnimation(pair.second, mLauncher);
        }
    }

    /**
     * Remote animation runner for animation to launch an app.
     */
    private class AppLaunchAnimationRunner implements RemoteAnimationFactory {

        private final View mV;
        private final RunnableList mOnEndCallback;

        AppLaunchAnimationRunner(View v, RunnableList onEndCallback) {
            mV = v;
            mOnEndCallback = onEndCallback;
        }

        @Override
        public void onAnimationStart(int transit,
                RemoteAnimationTarget[] appTargets,
                RemoteAnimationTarget[] wallpaperTargets,
                RemoteAnimationTarget[] nonAppTargets,
                LauncherAnimationRunner.AnimationResult result) {
            AnimatorSet anim = new AnimatorSet();
            boolean launcherClosing =
                    launcherIsATargetWithMode(appTargets, MODE_CLOSING);

            final boolean launchingFromWidget = mV instanceof LauncherAppWidgetHostView;
            final boolean launchingFromRecents = isLaunchingFromRecents(mV, appTargets);
            final boolean skipFirstFrame;
            if (launchingFromWidget) {
                composeWidgetLaunchAnimator(anim, (LauncherAppWidgetHostView) mV, appTargets,
                        wallpaperTargets, nonAppTargets, launcherClosing);
                addCujInstrumentation(anim, Cuj.CUJ_LAUNCHER_APP_LAUNCH_FROM_WIDGET);
                skipFirstFrame = true;
            } else if (launchingFromRecents) {
                composeRecentsLaunchAnimator(anim, mV, appTargets, wallpaperTargets, nonAppTargets,
                        launcherClosing);
                addCujInstrumentation(
                        anim, Cuj.CUJ_LAUNCHER_APP_LAUNCH_FROM_RECENTS);
                skipFirstFrame = true;
            } else {
                composeIconLaunchAnimator(anim, mV, appTargets, wallpaperTargets, nonAppTargets,
                        launcherClosing);
                addCujInstrumentation(anim, Cuj.CUJ_LAUNCHER_APP_LAUNCH_FROM_ICON);
                skipFirstFrame = false;
            }

            if (launcherClosing) {
                anim.addListener(mForceInvisibleListener);
            }

            result.setAnimation(anim, mLauncher, mOnEndCallback::executeAllAndDestroy,
                    skipFirstFrame);
        }

        @Override
        public void onAnimationCancelled() {
            mOnEndCallback.executeAllAndDestroy();
        }
    }

    /** Remote animation runner to launch an app using System UI's animation library. */
    private static class ContainerAnimationRunner implements RemoteAnimationFactory {

        /** The delegate runner that handles the actual animation. */
        private final RemoteAnimationDelegate<IRemoteAnimationFinishedCallback> mDelegate;

        private ContainerAnimationRunner(
                RemoteAnimationDelegate<IRemoteAnimationFinishedCallback> delegate) {
            mDelegate = delegate;
        }

        @Nullable
        private static ContainerAnimationRunner from(View v, Launcher launcher,
                StartingWindowListener startingWindowListener, RunnableList onEndCallback) {
            View viewToUse = findLaunchableViewWithBackground(v);
            if (viewToUse == null) {
                return null;
            }

            // The CUJ is logged by the click handler, so we don't log it inside the animation
            // library.
            ActivityTransitionAnimator.Controller controllerDelegate =
                    ActivityTransitionAnimator.Controller.fromView(viewToUse, null /* cujType */);

            if (controllerDelegate == null) {
                return null;
            }

            // This wrapper allows us to override the default value, telling the controller that the
            // current window is below the animating window.
            ActivityTransitionAnimator.Controller controller =
                    new DelegateTransitionAnimatorController(controllerDelegate) {
                        @Override
                        public boolean isBelowAnimatingWindow() {
                            return true;
                        }
                    };

            ActivityTransitionAnimator.Callback callback = task -> {
                final int backgroundColor =
                        startingWindowListener.mBackgroundColor == Color.TRANSPARENT
                                ? launcher.getScrimView().getBackgroundColor()
                                : startingWindowListener.mBackgroundColor;
                return ColorUtils.setAlphaComponent(backgroundColor, 255);
            };

            ActivityTransitionAnimator.Listener listener =
                    new ActivityTransitionAnimator.Listener() {
                        @Override
                        public void onTransitionAnimationEnd() {
                            onEndCallback.executeAllAndDestroy();
                        }
                    };

            return new ContainerAnimationRunner(
                    new ActivityTransitionAnimator.AnimationDelegate(
                            controller, callback, listener));
        }

        /**
         * Finds the closest parent of [view] (inclusive) that implements {@link LaunchableView} and
         * has a background drawable.
         */
        @Nullable
        private static <T extends View & LaunchableView> T findLaunchableViewWithBackground(
                View view) {
            View current = view;
            while (current.getBackground() == null || !(current instanceof LaunchableView)) {
                if (!(current.getParent() instanceof View)) {
                    return null;
                }

                current = (View) current.getParent();
            }

            return (T) current;
        }

        @Override
        public void onAnimationStart(int transit, RemoteAnimationTarget[] appTargets,
                RemoteAnimationTarget[] wallpaperTargets, RemoteAnimationTarget[] nonAppTargets,
                LauncherAnimationRunner.AnimationResult result) {
            mDelegate.onAnimationStart(
                    transit, appTargets, wallpaperTargets, nonAppTargets, result);
        }

        @Override
        public void onAnimationCancelled() {
            mDelegate.onAnimationCancelled();
        }
    }

    /**
     * Class that holds all the variables for the app open animation.
     */
    static class AnimOpenProperties {

        public final int cropCenterXStart;
        public final int cropCenterYStart;
        public final int cropWidthStart;
        public final int cropHeightStart;

        public final int cropCenterXEnd;
        public final int cropCenterYEnd;
        public final int cropWidthEnd;
        public final int cropHeightEnd;

        public final float dX;
        public final float dY;

        public final float initialAppIconScale;
        public final float finalAppIconScale;

        public final float iconAlphaStart;

        AnimOpenProperties(Resources r, DeviceProfile dp, Rect windowTargetBounds,
                RectF launcherIconBounds, View view, int dragLayerLeft, int dragLayerTop,
                boolean hasSplashScreen, boolean hasDifferentAppIcon) {
            // Scale the app icon to take up the entire screen. This simplifies the math when
            // animating the app window position / scale.
            float smallestSize = Math.min(windowTargetBounds.height(), windowTargetBounds.width());
            float maxScaleX = smallestSize / launcherIconBounds.width();
            float maxScaleY = smallestSize / launcherIconBounds.height();
            float iconStartScale = 1f;
            if (view instanceof BubbleTextView && !(view.getParent() instanceof DeepShortcutView)) {
                Drawable dr = ((BubbleTextView) view).getIcon();
                if (dr instanceof FastBitmapDrawable) {
                    iconStartScale = ((FastBitmapDrawable) dr).getAnimatedScale();
                }
            }

            initialAppIconScale = iconStartScale;
            finalAppIconScale = Math.max(maxScaleX, maxScaleY);

            // Animate the app icon to the center of the window bounds in screen coordinates.
            float centerX = windowTargetBounds.centerX() - dragLayerLeft;
            float centerY = windowTargetBounds.centerY() - dragLayerTop;

            dX = centerX - launcherIconBounds.centerX();
            dY = centerY - launcherIconBounds.centerY();

            iconAlphaStart = hasSplashScreen && !hasDifferentAppIcon ? 0 : 1f;

            final int windowIconSize = ResourceUtils.getDimenByName("starting_surface_icon_size",
                    r, 108);

            cropCenterXStart = windowTargetBounds.centerX();
            cropCenterYStart = windowTargetBounds.centerY();

            cropWidthStart = windowIconSize;
            cropHeightStart = windowIconSize;

            cropWidthEnd = windowTargetBounds.width();
            cropHeightEnd = windowTargetBounds.height();

            cropCenterXEnd = windowTargetBounds.centerX();
            cropCenterYEnd = windowTargetBounds.centerY();
        }
    }

    private static class StartingWindowListener extends IStartingWindowListener.Stub {
        private final WeakReference<QuickstepTransitionManager> mTransitionManagerRef;
        private int mBackgroundColor;

        private StartingWindowListener(QuickstepTransitionManager transitionManager) {
            mTransitionManagerRef = new WeakReference<>(transitionManager);
        }

        @Override
        public void onTaskLaunching(int taskId, int supportedType, int color) {
            QuickstepTransitionManager transitionManager = mTransitionManagerRef.get();
            if (transitionManager != null) {
                transitionManager.mTaskStartParams.put(taskId, Pair.create(supportedType, color));
            }
            mBackgroundColor = color;
        }
    }

    /**
     * Transfer the rectangle to another coordinate if needed.
     *
     * @param toLauncher which one is the anchor of this transfer, if true then transfer from
     *                   animation target to launcher, false transfer from launcher to animation
     *                   target.
     */
    public void transferRectToTargetCoordinate(RemoteAnimationTarget target, RectF currentRect,
            boolean toLauncher, RectF resultRect) {
        mCoordinateTransfer.transferRectToTargetCoordinate(
                target, currentRect, toLauncher, resultRect);
    }

    private static class RemoteAnimationCoordinateTransfer {
        private final QuickstepLauncher mLauncher;
        private final Rect mDisplayRect = new Rect();
        private final Rect mTmpResult = new Rect();

        RemoteAnimationCoordinateTransfer(QuickstepLauncher launcher) {
            mLauncher = launcher;
        }

        void transferRectToTargetCoordinate(RemoteAnimationTarget target, RectF currentRect,
                boolean toLauncher, RectF resultRect) {
            final int taskRotation = target.windowConfiguration.getRotation();
            final DeviceProfile profile = mLauncher.getDeviceProfile();

            final int rotationDelta = toLauncher
                    ? android.util.RotationUtils.deltaRotation(taskRotation, profile.rotationHint)
                    : android.util.RotationUtils.deltaRotation(profile.rotationHint, taskRotation);
            if (rotationDelta != ROTATION_0) {
                // Get original display size when task is on top but with different rotation
                if (rotationDelta % 2 != 0 && toLauncher && (profile.rotationHint == ROTATION_0
                        || profile.rotationHint == ROTATION_180)) {
                    mDisplayRect.set(0, 0, profile.heightPx, profile.widthPx);
                } else {
                    mDisplayRect.set(0, 0, profile.widthPx, profile.heightPx);
                }
                currentRect.round(mTmpResult);
                android.util.RotationUtils.rotateBounds(mTmpResult, mDisplayRect, rotationDelta);
                resultRect.set(mTmpResult);
            } else {
                resultRect.set(currentRect);
            }
        }
    }

    /**
     * RectFSpringAnim update listener to be used for app to home animation.
     */
    private class SpringAnimRunner implements RectFSpringAnim.OnUpdateListener {
        private final RemoteAnimationTarget[] mAppTargets;
        private final Matrix mMatrix = new Matrix();
        private final Point mTmpPos = new Point();
        private final RectF mCurrentRectF = new RectF();
        private final float mStartRadius;
        private final float mEndRadius;
        private final SurfaceTransactionApplier mSurfaceApplier;
        private final Rect mWindowStartBounds = new Rect();
        private final Rect mWindowOriginalBounds = new Rect();

        private final Rect mTmpRect = new Rect();

        /**
         * Constructor for SpringAnimRunner
         *
         * @param appTargets                the list of opening/closing apps
         * @param targetRect                target rectangle
         * @param closingWindowStartRect    start position of the window when the spring animation
         *                                  is started. In the predictive back to home case this
         *                                  will be smaller than closingWindowOriginalRect because
         *                                  the window is already scaled by the user gesture
         * @param closingWindowOriginalRect Original unscaled window rect
         * @param startWindowCornerRadius   corner radius of window at the start position
         */
        SpringAnimRunner(RemoteAnimationTarget[] appTargets, RectF targetRect,
                Rect closingWindowStartRect, Rect closingWindowOriginalRect,
                float startWindowCornerRadius) {
            mAppTargets = appTargets;
            mStartRadius = startWindowCornerRadius;
            mEndRadius = Math.max(1, targetRect.width()) / 2f;
            mSurfaceApplier = new SurfaceTransactionApplier(mDragLayer);
            mWindowStartBounds.set(closingWindowStartRect);
            mWindowOriginalBounds.set(closingWindowOriginalRect);

            // transfer the coordinate based on animation target.
            if (mAppTargets != null) {
                for (RemoteAnimationTarget t : mAppTargets) {
                    if (t.mode == MODE_CLOSING) {
                        final RectF transferRect = new RectF(mWindowStartBounds);
                        final RectF result = new RectF();
                        transferRectToTargetCoordinate(t, transferRect, false, result);
                        result.round(mWindowStartBounds);

                        transferRect.set(closingWindowOriginalRect);
                        transferRectToTargetCoordinate(t, transferRect, false, result);
                        result.round(mWindowOriginalBounds);
                        break;
                    }
                }
            }
        }

        public float getCornerRadius(float progress) {
            return Utilities.mapRange(progress, mStartRadius, mEndRadius);
        }

        @Override
        public void onUpdate(RectF currentRectF, float progress) {
            SurfaceTransaction transaction = new SurfaceTransaction();
            for (int i = mAppTargets.length - 1; i >= 0; i--) {
                RemoteAnimationTarget target = mAppTargets[i];
                SurfaceProperties builder = transaction.forSurface(target.leash);

                if (target.localBounds != null) {
                    mTmpPos.set(target.localBounds.left, target.localBounds.top);
                } else {
                    mTmpPos.set(target.position.x, target.position.y);
                }

                if (target.mode == MODE_CLOSING) {
                    transferRectToTargetCoordinate(target, currentRectF, false, mCurrentRectF);

                    // Scale the target window to match the currentRectF.
                    final float scale;

                    // We need to infer the crop (we crop the window to match the currentRectF).
                    if (mWindowStartBounds.height() > mWindowStartBounds.width()) {
                        scale = Math.min(1f, mCurrentRectF.width() / mWindowOriginalBounds.width());

                        int unscaledHeight = (int) (mCurrentRectF.height() * (1f / scale));
                        int croppedHeight = mWindowStartBounds.height() - unscaledHeight;
                        mTmpRect.set(0, 0, mWindowOriginalBounds.width(),
                                mWindowStartBounds.height() - croppedHeight);
                    } else {
                        scale = Math.min(1f, mCurrentRectF.height()
                                / mWindowOriginalBounds.height());

                        int unscaledWidth = (int) (mCurrentRectF.width() * (1f / scale));
                        int croppedWidth = mWindowStartBounds.width() - unscaledWidth;
                        mTmpRect.set(0, 0, mWindowStartBounds.width() - croppedWidth,
                                mWindowOriginalBounds.height());
                    }

                    // Match size and position of currentRect.
                    mMatrix.setScale(scale, scale);
                    mMatrix.postTranslate(mCurrentRectF.left, mCurrentRectF.top);

                    builder.setMatrix(mMatrix)
                            .setWindowCrop(mTmpRect)
                            .setAlpha(getWindowAlpha(progress))
                            .setCornerRadius(getCornerRadius(progress) / scale);
                } else if (target.mode == MODE_OPENING) {
                    mMatrix.setTranslate(mTmpPos.x, mTmpPos.y);
                    builder.setMatrix(mMatrix)
                            .setAlpha(1f);
                }
            }
            mSurfaceApplier.scheduleApply(transaction);
        }

        protected float getWindowAlpha(float progress) {
            // Alpha interpolates between [1, 0] between progress values [start, end]
            final float start = 0f;
            final float end = 0.85f;

            if (progress <= start) {
                return 1f;
            }
            if (progress >= end) {
                return 0f;
            }
            return Utilities.mapToRange(progress, start, end, 1, 0, ACCELERATE_1_5);
        }
    }

    private static class LaunchDepthController extends DepthController {
        LaunchDepthController(QuickstepLauncher launcher) {
            super(launcher);
            setCrossWindowBlursEnabled(
                    CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled());
            // Make sure that the starting value matches the current depth set by the main
            // controller.
            stateDepth.setValue(launcher.getDepthController().stateDepth.getValue());
        }
    }
}
