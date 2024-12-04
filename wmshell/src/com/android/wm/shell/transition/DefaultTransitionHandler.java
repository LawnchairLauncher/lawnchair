/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.transition;

import static android.app.ActivityOptions.ANIM_CLIP_REVEAL;
import static android.app.ActivityOptions.ANIM_CUSTOM;
import static android.app.ActivityOptions.ANIM_NONE;
import static android.app.ActivityOptions.ANIM_OPEN_CROSS_PROFILE_APPS;
import static android.app.ActivityOptions.ANIM_SCALE_UP;
import static android.app.ActivityOptions.ANIM_SCENE_TRANSITION;
import static android.app.ActivityOptions.ANIM_THUMBNAIL_SCALE_DOWN;
import static android.app.ActivityOptions.ANIM_THUMBNAIL_SCALE_UP;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED;
import static android.app.admin.DevicePolicyManager.EXTRA_RESOURCE_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_RESOURCE_TYPE_DRAWABLE;
import static android.app.admin.DevicePolicyResources.Drawables.Source.PROFILE_SWITCH_ANIMATION;
import static android.app.admin.DevicePolicyResources.Drawables.Style.OUTLINE;
import static android.app.admin.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_UNSPECIFIED;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_KEYGUARD_UNOCCLUDE;
import static android.view.WindowManager.TRANSIT_RELAUNCH;
import static android.window.TransitionInfo.FLAG_CROSS_PROFILE_OWNER_THUMBNAIL;
import static android.window.TransitionInfo.FLAG_CROSS_PROFILE_WORK_THUMBNAIL;
import static android.window.TransitionInfo.FLAG_DISPLAY_HAS_ALERT_WINDOWS;
import static android.window.TransitionInfo.FLAG_FILLS_TASK;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;
import static android.window.TransitionInfo.FLAG_IS_BEHIND_STARTING_WINDOW;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;
import static android.window.TransitionInfo.FLAG_IS_VOICE_INTERACTION;
import static android.window.TransitionInfo.FLAG_IS_WALLPAPER;
import static android.window.TransitionInfo.FLAG_SHOW_WALLPAPER;
import static android.window.TransitionInfo.FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_CLOSE;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_INTRA_CLOSE;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_INTRA_OPEN;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_NONE;
import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_OPEN;
import static com.android.wm.shell.transition.TransitionAnimationHelper.edgeExtendWindow;
import static com.android.wm.shell.transition.TransitionAnimationHelper.getTransitionBackgroundColorIfSet;
import static com.android.wm.shell.transition.TransitionAnimationHelper.getTransitionTypeFromInfo;
import static com.android.wm.shell.transition.TransitionAnimationHelper.loadAttributeAnimation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.view.SurfaceSession;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.window.TransitionInfo;
import android.window.TransitionMetrics;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.policy.ScreenDecorationsUtils;
import com.android.internal.policy.TransitionAnimation;
import com.android.internal.protolog.common.ProtoLog;
import com.android.window.flags.Flags;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.sysui.ShellInit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** The default handler that handles anything not already handled. */
public class DefaultTransitionHandler implements Transitions.TransitionHandler {
    private static final int MAX_ANIMATION_DURATION = 3000;

    private final TransactionPool mTransactionPool;
    private final DisplayController mDisplayController;
    private final Context mContext;
    private final Handler mMainHandler;
    private final ShellExecutor mMainExecutor;
    private final ShellExecutor mAnimExecutor;
    private final TransitionAnimation mTransitionAnimation;
    private final DevicePolicyManager mDevicePolicyManager;

    private final SurfaceSession mSurfaceSession = new SurfaceSession();

    /** Keeps track of the currently-running animations associated with each transition. */
    private final ArrayMap<IBinder, ArrayList<Animator>> mAnimations = new ArrayMap<>();

    private final CounterRotatorHelper mRotator = new CounterRotatorHelper();
    private final Rect mInsets = new Rect(0, 0, 0, 0);
    private float mTransitionAnimationScaleSetting = 1.0f;

    private final RootTaskDisplayAreaOrganizer mRootTDAOrganizer;
    private final int mCurrentUserId;

    private Drawable mEnterpriseThumbnailDrawable;

    private BroadcastReceiver mEnterpriseResourceUpdatedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getIntExtra(EXTRA_RESOURCE_TYPE, /* default= */ -1)
                    != EXTRA_RESOURCE_TYPE_DRAWABLE) {
                return;
            }
            updateEnterpriseThumbnailDrawable();
        }
    };

    DefaultTransitionHandler(@NonNull Context context,
            @NonNull ShellInit shellInit,
            @NonNull DisplayController displayController,
            @NonNull TransactionPool transactionPool,
            @NonNull ShellExecutor mainExecutor, @NonNull Handler mainHandler,
            @NonNull ShellExecutor animExecutor,
            @NonNull RootTaskDisplayAreaOrganizer rootTDAOrganizer) {
        mDisplayController = displayController;
        mTransactionPool = transactionPool;
        mContext = context;
        mMainHandler = mainHandler;
        mMainExecutor = mainExecutor;
        mAnimExecutor = animExecutor;
        mTransitionAnimation = new TransitionAnimation(context, false /* debug */, Transitions.TAG);
        mCurrentUserId = UserHandle.myUserId();
        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
        shellInit.addInitCallback(this::onInit, this);
        mRootTDAOrganizer = rootTDAOrganizer;
    }

    private void onInit() {
        updateEnterpriseThumbnailDrawable();
        mContext.registerReceiver(
                mEnterpriseResourceUpdatedReceiver,
                new IntentFilter(ACTION_DEVICE_POLICY_RESOURCE_UPDATED),
                /* broadcastPermission = */ null,
                mMainHandler);

        TransitionAnimation.initAttributeCache(mContext, mMainHandler);
    }

    private void updateEnterpriseThumbnailDrawable() {
        mEnterpriseThumbnailDrawable = mDevicePolicyManager.getResources().getDrawable(
                WORK_PROFILE_ICON, OUTLINE, PROFILE_SWITCH_ANIMATION,
                () -> mContext.getDrawable(R.drawable.ic_corp_badge));
    }

    @VisibleForTesting
    static int getRotationAnimationHint(@NonNull TransitionInfo.Change displayChange,
            @NonNull TransitionInfo info, @NonNull DisplayController displayController) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "Display is changing, resolve the animation hint.");
        // The explicit request of display has the highest priority.
        if (displayChange.getRotationAnimation() == ROTATION_ANIMATION_SEAMLESS) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "  display requests explicit seamless");
            return ROTATION_ANIMATION_SEAMLESS;
        }

        boolean allTasksSeamless = false;
        boolean rejectSeamless = false;
        ActivityManager.RunningTaskInfo topTaskInfo = null;
        int animationHint = ROTATION_ANIMATION_ROTATE;
        // Traverse in top-to-bottom order so that the first task is top-most.
        final int size = info.getChanges().size();
        for (int i = 0; i < size; ++i) {
            final TransitionInfo.Change change = info.getChanges().get(i);

            // Only look at changing things. showing/hiding don't need to rotate.
            if (change.getMode() != TRANSIT_CHANGE) continue;

            // This container isn't rotating, so we can ignore it.
            if (change.getEndRotation() == change.getStartRotation()) continue;
            if ((change.getFlags() & FLAG_IS_DISPLAY) != 0) {
                // In the presence of System Alert windows we can not seamlessly rotate.
                if ((change.getFlags() & FLAG_DISPLAY_HAS_ALERT_WINDOWS) != 0) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "  display has system alert windows, so not seamless.");
                    rejectSeamless = true;
                }
            } else if ((change.getFlags() & FLAG_IS_WALLPAPER) != 0) {
                if (change.getRotationAnimation() != ROTATION_ANIMATION_SEAMLESS) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "  wallpaper is participating but isn't seamless.");
                    rejectSeamless = true;
                }
            } else if (change.getTaskInfo() != null) {
                final int anim = change.getRotationAnimation();
                final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
                final boolean isTopTask = topTaskInfo == null;
                if (isTopTask) {
                    topTaskInfo = taskInfo;
                    if (anim != ROTATION_ANIMATION_UNSPECIFIED
                            && anim != ROTATION_ANIMATION_SEAMLESS) {
                        animationHint = anim;
                    }
                }
                // We only enable seamless rotation if all the visible task windows requested it.
                if (anim != ROTATION_ANIMATION_SEAMLESS) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "  task %s isn't requesting seamless, so not seamless.",
                            taskInfo.taskId);
                    allTasksSeamless = false;
                } else if (isTopTask) {
                    allTasksSeamless = true;
                }
            }
        }

        if (!allTasksSeamless || rejectSeamless) {
            return animationHint;
        }

        // This is the only way to get display-id currently, so check display capabilities here.
        final DisplayLayout displayLayout = displayController.getDisplayLayout(
                topTaskInfo.displayId);
        // This condition should be true when using gesture navigation or the screen size is large
        // (>600dp) because the bar is small relative to screen.
        if (displayLayout.allowSeamlessRotationDespiteNavBarMoving()) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "  nav bar allows seamless.");
            return ROTATION_ANIMATION_SEAMLESS;
        }
        // For the upside down rotation we don't rotate seamlessly as the navigation bar moves
        // position. Note most apps (using orientation:sensor or user as opposed to fullSensor)
        // will not enter the reverse portrait orientation, so actually the orientation won't
        // change at all.
        final int upsideDownRotation = displayLayout.getUpsideDownRotation();
        if (displayChange.getStartRotation() == upsideDownRotation
                || displayChange.getEndRotation() == upsideDownRotation) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "  rotation involves upside-down portrait, so not seamless.");
            return animationHint;
        }

        // If the navigation bar cannot change sides, then it will jump when changing orientation
        // so do not use seamless rotation.
        if (!displayLayout.navigationBarCanMove()) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                    "  nav bar changes sides, so not seamless.");
            return animationHint;
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "  Rotation IS seamless.");
        return ROTATION_ANIMATION_SEAMLESS;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                "start default transition animation, info = %s", info);
        // If keyguard goes away, we should loadKeyguardExitAnimation. Otherwise this just
        // immediately finishes since there is no animation for screen-wake.
        if (info.getType() == WindowManager.TRANSIT_WAKE && !info.isKeyguardGoingAway()) {
            startTransaction.apply();
            finishCallback.onTransitionFinished(null /* wct */);
            return true;
        }

        // Early check if the transition doesn't warrant an animation.
        if (Transitions.isAllNoAnimation(info) || Transitions.isAllOrderOnly(info)
                || (info.getFlags() & WindowManager.TRANSIT_FLAG_INVISIBLE) != 0) {
            startTransaction.apply();
            finishTransaction.apply();
            finishCallback.onTransitionFinished(null /* wct */);
            return true;
        }

        if (mAnimations.containsKey(transition)) {
            throw new IllegalStateException("Got a duplicate startAnimation call for "
                    + transition);
        }
        final ArrayList<Animator> animations = new ArrayList<>();
        mAnimations.put(transition, animations);

        final Runnable onAnimFinish = () -> {
            if (!animations.isEmpty()) return;
            mAnimations.remove(transition);
            finishCallback.onTransitionFinished(null /* wct */);
        };

        final List<Consumer<SurfaceControl.Transaction>> postStartTransactionCallbacks =
                new ArrayList<>();

        @ColorInt int backgroundColorForTransition = 0;
        final int wallpaperTransit = getWallpaperTransitType(info);
        boolean isDisplayRotationAnimationStarted = false;
        final boolean isDreamTransition = isDreamTransition(info);
        final boolean isOnlyTranslucent = isOnlyTranslucent(info);
        final boolean isActivityLevel = isActivityLevelOnly(info);

        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.hasAllFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY
                    | FLAG_IS_BEHIND_STARTING_WINDOW)) {
                // Don't animate embedded activity if it is covered by the starting window.
                // Non-embedded case still needs animation because the container can still animate
                // the starting window together, e.g. CLOSE or CHANGE type.
                continue;
            }
            if (change.hasFlags(TransitionInfo.FLAGS_IS_NON_APP_WINDOW)) {
                // Wallpaper, IME, and system windows don't need any default animations.
                continue;
            }
            final boolean isTask = change.getTaskInfo() != null;
            final int mode = change.getMode();
            boolean isSeamlessDisplayChange = false;

            if (mode == TRANSIT_CHANGE && change.hasFlags(FLAG_IS_DISPLAY)) {
                if (info.getType() == TRANSIT_CHANGE) {
                    final int anim = getRotationAnimationHint(change, info, mDisplayController);
                    isSeamlessDisplayChange = anim == ROTATION_ANIMATION_SEAMLESS;
                    if (!(isSeamlessDisplayChange || anim == ROTATION_ANIMATION_JUMPCUT)) {
                        startRotationAnimation(startTransaction, change, info, anim, animations,
                                onAnimFinish);
                        isDisplayRotationAnimationStarted = true;
                        continue;
                    }
                } else {
                    // Opening/closing an app into a new orientation.
                    mRotator.handleClosingChanges(info, startTransaction, change);
                }
            }

            if (mode == TRANSIT_CHANGE) {
                // If task is child task, only set position in parent and update crop when needed.
                if (isTask && change.getParent() != null
                        && info.getChange(change.getParent()).getTaskInfo() != null) {
                    final Point positionInParent = change.getTaskInfo().positionInParent;
                    startTransaction.setPosition(change.getLeash(),
                            positionInParent.x, positionInParent.y);

                    if (!change.getEndAbsBounds().equals(
                            info.getChange(change.getParent()).getEndAbsBounds())) {
                        startTransaction.setWindowCrop(change.getLeash(),
                                change.getEndAbsBounds().width(),
                                change.getEndAbsBounds().height());
                    }

                    continue;
                }

                // There is no default animation for Pip window in rotation transition, and the
                // PipTransition will update the surface of its own window at start/finish.
                if (isTask && change.getTaskInfo().configuration.windowConfiguration
                        .getWindowingMode() == WINDOWING_MODE_PINNED) {
                    continue;
                }
                // No default animation for this, so just update bounds/position.
                final int rootIdx = TransitionUtil.rootIndexFor(change, info);
                startTransaction.setPosition(change.getLeash(),
                        change.getEndAbsBounds().left - info.getRoot(rootIdx).getOffset().x,
                        change.getEndAbsBounds().top - info.getRoot(rootIdx).getOffset().y);
                // Seamless display transition doesn't need to animate.
                if (isSeamlessDisplayChange) continue;
                if (isTask || (change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY)
                        && !change.hasFlags(FLAG_FILLS_TASK))) {
                    // Update Task and embedded split window crop bounds, otherwise we may see crop
                    // on previous bounds during the rotation animation.
                    startTransaction.setWindowCrop(change.getLeash(),
                            change.getEndAbsBounds().width(), change.getEndAbsBounds().height());
                }
                // Rotation change of independent non display window container.
                if (change.getParent() == null && !change.hasFlags(FLAG_IS_DISPLAY)
                        && change.getStartRotation() != change.getEndRotation()) {
                    startRotationAnimation(startTransaction, change, info,
                            ROTATION_ANIMATION_ROTATE, animations, onAnimFinish);
                    continue;
                }
            }

            // Hide the invisible surface directly without animating it if there is a display
            // rotation animation playing.
            if (isDisplayRotationAnimationStarted && TransitionUtil.isClosingType(mode)) {
                startTransaction.hide(change.getLeash());
                continue;
            }

            // Don't animate anything that isn't independent.
            if (!TransitionInfo.isIndependent(change, info)) continue;

            final int type = getTransitionTypeFromInfo(info);
            Animation a = loadAnimation(type, info, change, wallpaperTransit, isDreamTransition);
            if (a != null) {
                if (isTask) {
                    final boolean isTranslucent = (change.getFlags() & FLAG_TRANSLUCENT) != 0;
                    if (!isTranslucent && TransitionUtil.isOpenOrCloseMode(mode)
                            && TransitionUtil.isOpenOrCloseMode(info.getType())
                            && wallpaperTransit == WALLPAPER_TRANSITION_NONE) {
                        // Use the overview background as the background for the animation
                        final Context uiContext = ActivityThread.currentActivityThread()
                                .getSystemUiContext();
                        backgroundColorForTransition =
                                uiContext.getColor(R.color.overview_background);
                    }
                    if (wallpaperTransit == WALLPAPER_TRANSITION_OPEN
                            && TransitionUtil.isOpeningType(info.getType())) {
                        // Need to flip the z-order of opening/closing because the WALLPAPER_OPEN
                        // always animates the closing task over the opening one while
                        // traditionally, an OPEN transition animates the opening over the closing.

                        // See Transitions#setupAnimHierarchy for details about these variables.
                        final int numChanges = info.getChanges().size();
                        final int zSplitLine = numChanges + 1;
                        if (TransitionUtil.isOpeningType(mode)) {
                            final int layer = zSplitLine - i;
                            startTransaction.setLayer(change.getLeash(), layer);
                        } else if (TransitionUtil.isClosingType(mode)) {
                            final int layer = zSplitLine + numChanges - i;
                            startTransaction.setLayer(change.getLeash(), layer);
                        }
                    } else if (isOnlyTranslucent && TransitionUtil.isOpeningType(info.getType())
                                && TransitionUtil.isClosingType(mode)) {
                        // If there is a closing translucent task in an OPENING transition, we will
                        // actually select a CLOSING animation, so move the closing task into
                        // the animating part of the z-order.

                        // See Transitions#setupAnimHierarchy for details about these variables.
                        final int numChanges = info.getChanges().size();
                        final int zSplitLine = numChanges + 1;
                        final int layer = zSplitLine + numChanges - i;
                        startTransaction.setLayer(change.getLeash(), layer);
                    }
                }

                final float cornerRadius;
                if (a.hasRoundedCorners()) {
                    final int displayId = isTask ? change.getTaskInfo().displayId
                            : info.getRoot(TransitionUtil.rootIndexFor(change, info))
                                    .getDisplayId();
                    final Context displayContext =
                            mDisplayController.getDisplayContext(displayId);
                    cornerRadius = displayContext == null ? 0
                            : ScreenDecorationsUtils.getWindowCornerRadius(displayContext);
                } else {
                    cornerRadius = 0;
                }

                backgroundColorForTransition = getTransitionBackgroundColorIfSet(info, change, a,
                        backgroundColorForTransition);

                if (!isTask && a.hasExtension()) {
                    if (!TransitionUtil.isOpeningType(mode)) {
                        // Can screenshot now (before startTransaction is applied)
                        edgeExtendWindow(change, a, startTransaction, finishTransaction);
                    } else {
                        // Need to screenshot after startTransaction is applied otherwise activity
                        // may not be visible or ready yet.
                        postStartTransactionCallbacks
                                .add(t -> edgeExtendWindow(change, a, t, finishTransaction));
                    }
                }

                final Rect clipRect = TransitionUtil.isClosingType(mode)
                        ? new Rect(mRotator.getEndBoundsInStartRotation(change))
                        : new Rect(change.getEndAbsBounds());
                clipRect.offsetTo(0, 0);

                final TransitionInfo.Root animRoot = TransitionUtil.getRootFor(change, info);
                final Point animRelOffset = new Point(
                        change.getEndAbsBounds().left - animRoot.getOffset().x,
                        change.getEndAbsBounds().top - animRoot.getOffset().y);

                if (change.getActivityComponent() != null) {
                    // For appcompat letterbox: we intentionally report the task-bounds so that we
                    // can animate as-if letterboxes are "part of" the activity. This means we can't
                    // always rely solely on endAbsBounds and need to also max with endRelOffset.
                    animRelOffset.x = Math.max(animRelOffset.x, change.getEndRelOffset().x);
                    animRelOffset.y = Math.max(animRelOffset.y, change.getEndRelOffset().y);
                }

                if (change.getActivityComponent() != null && !isActivityLevel) {
                    // At this point, this is an independent activity change in a non-activity
                    // transition. This means that an activity transition got erroneously combined
                    // with another ongoing transition. This then means that the animation root may
                    // not tightly fit the activities, so we have to put them in a separate crop.
                    final int layer = Transitions.calculateAnimLayer(change, i,
                            info.getChanges().size(), info.getType());
                    final SurfaceControl leash = new SurfaceControl.Builder()
                            .setName("Transition ActivityWrap: "
                                    + change.getActivityComponent().toShortString())
                            .setParent(animRoot.getLeash())
                            .setContainerLayer().build();
                    startTransaction.setCrop(leash, clipRect);
                    startTransaction.setPosition(leash, animRelOffset.x, animRelOffset.y);
                    startTransaction.setLayer(leash, layer);
                    startTransaction.show(leash);
                    startTransaction.reparent(change.getLeash(), leash);
                    startTransaction.setPosition(change.getLeash(), 0, 0);
                    animRelOffset.set(0, 0);
                    finishTransaction.reparent(leash, null);
                    leash.release();
                }

                buildSurfaceAnimation(animations, a, change.getLeash(), onAnimFinish,
                        mTransactionPool, mMainExecutor, animRelOffset, cornerRadius,
                        clipRect);

                final TransitionInfo.AnimationOptions options;
                if (Flags.moveAnimationOptionsToChange()) {
                    options = info.getAnimationOptions();
                } else {
                    options = change.getAnimationOptions();
                }
                if (options != null) {
                    attachThumbnail(animations, onAnimFinish, change, info.getAnimationOptions(),
                            cornerRadius);
                }
            }
        }

        if (backgroundColorForTransition != 0) {
            addBackgroundColor(info, backgroundColorForTransition, startTransaction,
                    finishTransaction);
        }

        if (postStartTransactionCallbacks.size() > 0) {
            // postStartTransactionCallbacks require that the start transaction is already
            // applied to run otherwise they may result in flickers and UI inconsistencies.
            startTransaction.apply(true /* sync */);
            // startTransaction is empty now, so fill it with the edge-extension setup
            for (Consumer<SurfaceControl.Transaction> postStartTransactionCallback :
                    postStartTransactionCallbacks) {
                postStartTransactionCallback.accept(startTransaction);
            }
        }
        startTransaction.apply();

        // now start animations. they are started on another thread, so we have to post them
        // *after* applying the startTransaction
        mAnimExecutor.execute(() -> {
            for (int i = 0; i < animations.size(); ++i) {
                animations.get(i).start();
            }
        });

        mRotator.cleanUp(finishTransaction);
        TransitionMetrics.getInstance().reportAnimationStart(transition);
        // run finish now in-case there are no animations
        onAnimFinish.run();
        return true;
    }

    private void addBackgroundColor(@NonNull TransitionInfo info,
            @ColorInt int color, @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        final Color bgColor = Color.valueOf(color);
        final float[] colorArray = new float[] { bgColor.red(), bgColor.green(), bgColor.blue() };

        for (int i = 0; i < info.getRootCount(); ++i) {
            final int displayId = info.getRoot(i).getDisplayId();
            final SurfaceControl.Builder colorLayerBuilder = new SurfaceControl.Builder()
                    .setName("animation-background")
                    .setCallsite("DefaultTransitionHandler")
                    .setColorLayer();

            // Attaching the background surface to the transition root could unexpectedly make it
            // cover one of the split root tasks. To avoid this, put the background surface just
            // above the display area when split is on.
            final boolean isSplitTaskInvolved =
                    info.getChanges().stream().anyMatch(c-> c.getTaskInfo() != null
                            && c.getTaskInfo().getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW);
            if (isSplitTaskInvolved) {
                mRootTDAOrganizer.attachToDisplayArea(displayId, colorLayerBuilder);
            } else {
                colorLayerBuilder.setParent(info.getRootLeash());
            }

            final SurfaceControl backgroundSurface = colorLayerBuilder.build();
            startTransaction.setColor(backgroundSurface, colorArray)
                    .setLayer(backgroundSurface, -1)
                    .show(backgroundSurface);
            finishTransaction.remove(backgroundSurface);
        }
    }

    private static boolean isDreamTransition(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().topActivityType == ACTIVITY_TYPE_DREAM) {
                return true;
            }
        }

        return false;
    }

    /**
     * Does `info` only contain translucent visibility changes (CHANGEs are ignored). We select
     * different animations and z-orders for these
     */
    private static boolean isOnlyTranslucent(@NonNull TransitionInfo info) {
        int translucentOpen = 0;
        int translucentClose = 0;
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getMode() == TRANSIT_CHANGE) continue;
            if (change.hasFlags(FLAG_TRANSLUCENT)) {
                if (TransitionUtil.isOpeningType(change.getMode())) {
                    translucentOpen += 1;
                } else {
                    translucentClose += 1;
                }
            } else {
                return false;
            }
        }
        return (translucentOpen + translucentClose) > 0;
    }

    /**
     * Does `info` only contain activity-level changes? This kinda assumes that if so, they are
     * all in one task.
     */
    private static boolean isActivityLevelOnly(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getActivityComponent() == null) return false;
        }
        return true;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ArrayList<Animator> anims = mAnimations.get(mergeTarget);
        if (anims == null) return;
        for (int i = anims.size() - 1; i >= 0; --i) {
            final Animator anim = anims.get(i);
            mAnimExecutor.execute(anim::end);
        }
    }

    private void startRotationAnimation(SurfaceControl.Transaction startTransaction,
            TransitionInfo.Change change, TransitionInfo info, int animHint,
            ArrayList<Animator> animations, Runnable onAnimFinish) {
        final int rootIdx = TransitionUtil.rootIndexFor(change, info);
        final ScreenRotationAnimation anim = new ScreenRotationAnimation(mContext, mSurfaceSession,
                mTransactionPool, startTransaction, change, info.getRoot(rootIdx).getLeash(),
                animHint);
        // The rotation animation may consist of 3 animations: fade-out screenshot, fade-in real
        // content, and background color. The item of "animGroup" will be removed if the sub
        // animation is finished. Then if the list becomes empty, the rotation animation is done.
        final ArrayList<Animator> animGroup = new ArrayList<>(3);
        final ArrayList<Animator> animGroupStore = new ArrayList<>(3);
        final Runnable finishCallback = () -> {
            if (!animGroup.isEmpty()) return;
            anim.kill();
            animations.removeAll(animGroupStore);
            onAnimFinish.run();
        };
        anim.buildAnimation(animGroup, finishCallback, mTransitionAnimationScaleSetting,
                mMainExecutor);
        for (int i = animGroup.size() - 1; i >= 0; i--) {
            final Animator animator = animGroup.get(i);
            animGroupStore.add(animator);
            animations.add(animator);
        }
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        return null;
    }

    @Override
    public void setAnimScaleSetting(float scale) {
        mTransitionAnimationScaleSetting = scale;
    }

    @Nullable
    private Animation loadAnimation(@WindowManager.TransitionType int type,
            @NonNull TransitionInfo info, @NonNull TransitionInfo.Change change,
            int wallpaperTransit, boolean isDreamTransition) {
        Animation a;

        final int flags = info.getFlags();
        final int changeMode = change.getMode();
        final int changeFlags = change.getFlags();
        final boolean isOpeningType = TransitionUtil.isOpeningType(type);
        final boolean enter = TransitionUtil.isOpeningType(changeMode);
        final boolean isTask = change.getTaskInfo() != null;
        final TransitionInfo.AnimationOptions options;
        if (Flags.moveAnimationOptionsToChange()) {
            options = change.getAnimationOptions();
        } else {
            options = info.getAnimationOptions();
        }
        final int overrideType = options != null ? options.getType() : ANIM_NONE;
        final Rect endBounds = TransitionUtil.isClosingType(changeMode)
                ? mRotator.getEndBoundsInStartRotation(change)
                : change.getEndAbsBounds();

        if (info.isKeyguardGoingAway()) {
            a = mTransitionAnimation.loadKeyguardExitAnimation(flags,
                    (changeFlags & FLAG_SHOW_WALLPAPER) != 0);
        } else if (type == TRANSIT_KEYGUARD_UNOCCLUDE) {
            a = mTransitionAnimation.loadKeyguardUnoccludeAnimation();
        } else if ((changeFlags & FLAG_IS_VOICE_INTERACTION) != 0) {
            if (isOpeningType) {
                a = mTransitionAnimation.loadVoiceActivityOpenAnimation(enter);
            } else {
                a = mTransitionAnimation.loadVoiceActivityExitAnimation(enter);
            }
        } else if (changeMode == TRANSIT_CHANGE) {
            // In the absence of a specific adapter, we just want to keep everything stationary.
            a = new AlphaAnimation(1.f, 1.f);
            a.setDuration(TransitionAnimation.DEFAULT_APP_TRANSITION_DURATION);
        } else if (type == TRANSIT_RELAUNCH) {
            a = mTransitionAnimation.createRelaunchAnimation(endBounds, mInsets, endBounds);
        } else if (overrideType == ANIM_CUSTOM
                && (!isTask || options.getOverrideTaskTransition())) {
            a = mTransitionAnimation.loadAnimationRes(options.getPackageName(), enter
                    ? options.getEnterResId() : options.getExitResId());
        } else if (overrideType == ANIM_OPEN_CROSS_PROFILE_APPS && enter) {
            a = mTransitionAnimation.loadCrossProfileAppEnterAnimation();
        } else if (overrideType == ANIM_CLIP_REVEAL) {
            a = mTransitionAnimation.createClipRevealAnimationLocked(type, wallpaperTransit, enter,
                    endBounds, endBounds, options.getTransitionBounds());
        } else if (overrideType == ANIM_SCALE_UP) {
            a = mTransitionAnimation.createScaleUpAnimationLocked(type, wallpaperTransit, enter,
                    endBounds, options.getTransitionBounds());
        } else if (overrideType == ANIM_THUMBNAIL_SCALE_UP
                || overrideType == ANIM_THUMBNAIL_SCALE_DOWN) {
            final boolean scaleUp = overrideType == ANIM_THUMBNAIL_SCALE_UP;
            a = mTransitionAnimation.createThumbnailEnterExitAnimationLocked(enter, scaleUp,
                    endBounds, type, wallpaperTransit, options.getThumbnail(),
                    options.getTransitionBounds());
        } else if ((changeFlags & FLAG_STARTING_WINDOW_TRANSFER_RECIPIENT) != 0 && isOpeningType) {
            // This received a transferred starting window, so don't animate
            return null;
        } else if (overrideType == ANIM_SCENE_TRANSITION) {
            // If there's a scene-transition, then jump-cut.
            return null;
        } else {
            a = loadAttributeAnimation(
                    type, info, change, wallpaperTransit, mTransitionAnimation, isDreamTransition);
        }

        if (a != null) {
            if (!a.isInitialized()) {
                final Rect animationRange = TransitionUtil.isClosingType(changeMode)
                        ? change.getStartAbsBounds() : change.getEndAbsBounds();
                a.initialize(animationRange.width(), animationRange.height(),
                        endBounds.width(), endBounds.height());
            }
            a.restrictDuration(MAX_ANIMATION_DURATION);
            a.scaleCurrentDuration(mTransitionAnimationScaleSetting);
        }
        return a;
    }

    /** Builds an animator for the surface and adds it to the `animations` list. */
    static void buildSurfaceAnimation(@NonNull ArrayList<Animator> animations,
            @NonNull Animation anim, @NonNull SurfaceControl leash,
            @NonNull Runnable finishCallback, @NonNull TransactionPool pool,
            @NonNull ShellExecutor mainExecutor, @Nullable Point position, float cornerRadius,
            @Nullable Rect clipRect) {
        final SurfaceControl.Transaction transaction = pool.acquire();
        final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        final Transformation transformation = new Transformation();
        final float[] matrix = new float[9];
        // Animation length is already expected to be scaled.
        va.overrideDurationScale(1.0f);
        va.setDuration(anim.computeDurationHint());
        final ValueAnimator.AnimatorUpdateListener updateListener = animation -> {
            final long currentPlayTime = Math.min(va.getDuration(), va.getCurrentPlayTime());

            applyTransformation(currentPlayTime, transaction, leash, anim, transformation, matrix,
                    position, cornerRadius, clipRect);
        };
        va.addUpdateListener(updateListener);

        final Runnable finisher = () -> {
            applyTransformation(va.getDuration(), transaction, leash, anim, transformation, matrix,
                    position, cornerRadius, clipRect);

            pool.release(transaction);
            mainExecutor.execute(() -> {
                animations.remove(va);
                finishCallback.run();
            });
        };
        va.addListener(new AnimatorListenerAdapter() {
            // It is possible for the end/cancel to be called more than once, which may cause
            // issues if the animating surface has already been released. Track the finished
            // state here to skip duplicate callbacks. See b/252872225.
            private boolean mFinished = false;

            @Override
            public void onAnimationEnd(Animator animation) {
                onFinish();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                onFinish();
            }

            private void onFinish() {
                if (mFinished) return;
                mFinished = true;
                finisher.run();
                // The update listener can continue to be called after the animation has ended if
                // end() is called manually again before the finisher removes the animation.
                // Remove it manually here to prevent animating a released surface.
                // See b/252872225.
                va.removeUpdateListener(updateListener);
            }
        });
        animations.add(va);
    }

    private void attachThumbnail(@NonNull ArrayList<Animator> animations,
            @NonNull Runnable finishCallback, TransitionInfo.Change change,
            TransitionInfo.AnimationOptions options, float cornerRadius) {
        final boolean isOpen = TransitionUtil.isOpeningType(change.getMode());
        final boolean isClose = TransitionUtil.isClosingType(change.getMode());
        if (isOpen) {
            if (options.getType() == ANIM_OPEN_CROSS_PROFILE_APPS) {
                attachCrossProfileThumbnailAnimation(animations, finishCallback, change,
                        cornerRadius);
            } else if (options.getType() == ANIM_THUMBNAIL_SCALE_UP) {
                attachThumbnailAnimation(animations, finishCallback, change, options, cornerRadius);
            }
        } else if (isClose && options.getType() == ANIM_THUMBNAIL_SCALE_DOWN) {
            attachThumbnailAnimation(animations, finishCallback, change, options, cornerRadius);
        }
    }

    private void attachCrossProfileThumbnailAnimation(@NonNull ArrayList<Animator> animations,
            @NonNull Runnable finishCallback, TransitionInfo.Change change, float cornerRadius) {
        final Rect bounds = change.getEndAbsBounds();
        // Show the right drawable depending on the user we're transitioning to.
        final Drawable thumbnailDrawable = change.hasFlags(FLAG_CROSS_PROFILE_OWNER_THUMBNAIL)
                        ? mContext.getDrawable(R.drawable.ic_account_circle)
                        : change.hasFlags(FLAG_CROSS_PROFILE_WORK_THUMBNAIL)
                                ? mEnterpriseThumbnailDrawable : null;
        if (thumbnailDrawable == null) {
            return;
        }
        final HardwareBuffer thumbnail = mTransitionAnimation.createCrossProfileAppsThumbnail(
                thumbnailDrawable, bounds);
        if (thumbnail == null) {
            return;
        }

        final SurfaceControl.Transaction transaction = mTransactionPool.acquire();
        final WindowThumbnail wt = WindowThumbnail.createAndAttach(mSurfaceSession,
                change.getLeash(), thumbnail, transaction);
        final Animation a =
                mTransitionAnimation.createCrossProfileAppsThumbnailAnimationLocked(bounds);
        if (a == null) {
            return;
        }

        final Runnable finisher = () -> {
            wt.destroy(transaction);
            mTransactionPool.release(transaction);

            finishCallback.run();
        };
        a.restrictDuration(MAX_ANIMATION_DURATION);
        a.scaleCurrentDuration(mTransitionAnimationScaleSetting);
        buildSurfaceAnimation(animations, a, wt.getSurface(), finisher, mTransactionPool,
                mMainExecutor, change.getEndRelOffset(), cornerRadius, change.getEndAbsBounds());
    }

    private void attachThumbnailAnimation(@NonNull ArrayList<Animator> animations,
            @NonNull Runnable finishCallback, TransitionInfo.Change change,
            TransitionInfo.AnimationOptions options, float cornerRadius) {
        final SurfaceControl.Transaction transaction = mTransactionPool.acquire();
        final WindowThumbnail wt = WindowThumbnail.createAndAttach(mSurfaceSession,
                change.getLeash(), options.getThumbnail(), transaction);
        final Rect bounds = change.getEndAbsBounds();
        final int orientation = mContext.getResources().getConfiguration().orientation;
        final Animation a = mTransitionAnimation.createThumbnailAspectScaleAnimationLocked(bounds,
                mInsets, options.getThumbnail(), orientation, null /* startRect */,
                options.getTransitionBounds(), options.getType() == ANIM_THUMBNAIL_SCALE_UP);

        final Runnable finisher = () -> {
            wt.destroy(transaction);
            mTransactionPool.release(transaction);

            finishCallback.run();
        };
        a.restrictDuration(MAX_ANIMATION_DURATION);
        a.scaleCurrentDuration(mTransitionAnimationScaleSetting);
        buildSurfaceAnimation(animations, a, wt.getSurface(), finisher, mTransactionPool,
                mMainExecutor, change.getEndRelOffset(), cornerRadius, change.getEndAbsBounds());
    }

    private static int getWallpaperTransitType(TransitionInfo info) {
        boolean hasOpenWallpaper = false;
        boolean hasCloseWallpaper = false;

        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if ((change.getFlags() & FLAG_SHOW_WALLPAPER) != 0) {
                if (TransitionUtil.isOpeningType(change.getMode())) {
                    hasOpenWallpaper = true;
                } else if (TransitionUtil.isClosingType(change.getMode())) {
                    hasCloseWallpaper = true;
                }
            }
        }

        if (hasOpenWallpaper && hasCloseWallpaper) {
            return TransitionUtil.isOpeningType(info.getType())
                    ? WALLPAPER_TRANSITION_INTRA_OPEN : WALLPAPER_TRANSITION_INTRA_CLOSE;
        } else if (hasOpenWallpaper) {
            return WALLPAPER_TRANSITION_OPEN;
        } else if (hasCloseWallpaper) {
            return WALLPAPER_TRANSITION_CLOSE;
        } else {
            return WALLPAPER_TRANSITION_NONE;
        }
    }

    /**
     * Returns {@code true} if the default transition handler can run the override animation.
     * @see #loadAnimation(TransitionInfo, TransitionInfo.Change, int, boolean)
     */
    public static boolean isSupportedOverrideAnimation(
            @NonNull TransitionInfo.AnimationOptions options) {
        final int animType = options.getType();
        return animType == ANIM_CUSTOM || animType == ANIM_SCALE_UP
                || animType == ANIM_THUMBNAIL_SCALE_UP || animType == ANIM_THUMBNAIL_SCALE_DOWN
                || animType == ANIM_CLIP_REVEAL || animType == ANIM_OPEN_CROSS_PROFILE_APPS;
    }

    private static void applyTransformation(long time, SurfaceControl.Transaction t,
            SurfaceControl leash, Animation anim, Transformation tmpTransformation, float[] matrix,
            Point position, float cornerRadius, @Nullable Rect immutableClipRect) {
        tmpTransformation.clear();
        anim.getTransformation(time, tmpTransformation);
        if (position != null) {
            tmpTransformation.getMatrix().postTranslate(position.x, position.y);
        }
        t.setMatrix(leash, tmpTransformation.getMatrix(), matrix);
        t.setAlpha(leash, tmpTransformation.getAlpha());

        final Rect clipRect = immutableClipRect == null ? null : new Rect(immutableClipRect);
        Insets extensionInsets = Insets.min(tmpTransformation.getInsets(), Insets.NONE);
        if (!extensionInsets.equals(Insets.NONE) && clipRect != null && !clipRect.isEmpty()) {
            // Clip out any overflowing edge extension
            clipRect.inset(extensionInsets);
            t.setCrop(leash, clipRect);
        }

        if (anim.hasRoundedCorners() && cornerRadius > 0 && clipRect != null) {
            // We can only apply rounded corner if a crop is set
            t.setCrop(leash, clipRect);
            t.setCornerRadius(leash, cornerRadius);
        }

        t.setFrameTimelineVsync(Choreographer.getInstance().getVsyncId());
        t.apply();
    }
}
