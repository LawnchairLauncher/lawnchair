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

package com.android.wm.shell.draganddrop;

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS;
import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_DENIED;
import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.ClipDescription.EXTRA_ACTIVITY_OPTIONS;
import static android.content.ClipDescription.EXTRA_PENDING_INTENT;
import static android.content.ClipDescription.MIMETYPE_APPLICATION_SHORTCUT;
import static android.content.ClipDescription.MIMETYPE_APPLICATION_TASK;
import static android.content.Intent.EXTRA_PACKAGE_NAME;
import static android.content.Intent.EXTRA_SHORTCUT_ID;
import static android.content.Intent.EXTRA_TASK_ID;
import static android.content.Intent.EXTRA_USER;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.wm.shell.Flags.enableFlexibleSplit;
import static com.android.wm.shell.draganddrop.DragLayout.DEBUG_LAYOUT;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_FULLSCREEN;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_BOTTOM;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_LEFT;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_RIGHT;
import static com.android.wm.shell.draganddrop.SplitDragPolicy.Target.TYPE_SPLIT_TOP;
import static com.android.wm.shell.shared.draganddrop.DragAndDropConstants.EXTRA_DISALLOW_HIT_REGION;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_0;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_1;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.util.Slog;
import android.view.View;
import android.window.WindowContainerToken;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.InstanceId;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.draganddrop.anim.DropTargetAnimSupplier;
import com.android.wm.shell.draganddrop.anim.HoverAnimProps;
import com.android.wm.shell.draganddrop.anim.TwoFiftyFiftyTargetAnimator;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.split.SplitScreenConstants;
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitIndex;
import com.android.wm.shell.shared.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.splitscreen.SplitScreenController;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import kotlin.Pair;

/**
 * The policy for handling drag and drop operations to shell.
 */
public class SplitDragPolicy implements DropTarget {

    private static final String TAG = SplitDragPolicy.class.getSimpleName();

    private final Context mContext;
    // Used only for launching a fullscreen task (or as a fallback if there is no split starter)
    private final Starter mFullscreenStarter;
    // Used for launching tasks into splitscreen
    private final Starter mSplitscreenStarter;
    private final DragZoneAnimator mDragZoneAnimator;
    private final SplitScreenController mSplitScreen;
    private ArrayList<SplitDragPolicy.Target> mTargets = new ArrayList<>();
    private final RectF mDisallowHitRegion = new RectF();
    /**
     * Maps a given SnapPosition to an array where each index of the array represents one
     * of the targets that are being hovered over, in order (Left to Right, Top to Bottom).
     * Ex: 4 drop targets when we're in 50/50 split
     * 2_50_50 => [ [AnimPropsTarget1, AnimPropsTarget2, AnimPropsTarget3, AnimPropsTarget4],
     *              ... // hovering over target 2,
     *              ... // hovering over target 3,
     *              ... // hovering over target 4
     *            ]
     */
    private final Map<Integer, List<List<HoverAnimProps>>> mHoverAnimProps = new HashMap();

    private InstanceId mLoggerSessionId;
    private DragSession mSession;
    @Nullable
    private Target mCurrentHoverTarget;
    /** This variable is a temporary placeholder, will be queried on drag start. */
    private int mCurrentSnapPosition = -1;

    public SplitDragPolicy(Context context, SplitScreenController splitScreen,
            DragZoneAnimator dragZoneAnimator) {
        this(context, splitScreen, new DefaultStarter(context), dragZoneAnimator);
    }

    @VisibleForTesting
    SplitDragPolicy(Context context, SplitScreenController splitScreen,
            Starter fullscreenStarter, DragZoneAnimator dragZoneAnimator) {
        mContext = context;
        mSplitScreen = splitScreen;
        mFullscreenStarter = fullscreenStarter;
        mSplitscreenStarter = splitScreen;
        mDragZoneAnimator = dragZoneAnimator;
    }

    /**
     * Starts a new drag session with the given initial drag data.
     */
    public void start(DragSession session, InstanceId loggerSessionId) {
        mLoggerSessionId = loggerSessionId;
        mSession = session;
        RectF disallowHitRegion = mSession.appData != null
                ? (RectF) mSession.appData.getExtra(EXTRA_DISALLOW_HIT_REGION)
                : null;
        if (disallowHitRegion == null) {
            mDisallowHitRegion.setEmpty();
        } else {
            mDisallowHitRegion.set(disallowHitRegion);
        }
    }

    /**
     * Returns the number of targets.
     */
    @Override
    public int getNumTargets() {
        return mTargets.size();
    }

    /**
     * Returns the target's regions based on the current state of the device and display.
     */
    @NonNull
    @Override
    public ArrayList<Target> getTargets(@NonNull Insets insets) {
        mTargets.clear();
        if (mSession == null) {
            // Return early if this isn't an app drag
            return mTargets;
        }

        final int w = mSession.displayLayout.width();
        final int h = mSession.displayLayout.height();
        final int iw = w - insets.left - insets.right;
        final int ih = h - insets.top - insets.bottom;
        final int l = insets.left;
        final int t = insets.top;
        final Rect displayRegion = new Rect(l, t, l + iw, t + ih);
        final Rect fullscreenDrawRegion = new Rect(displayRegion);
        final Rect fullscreenHitRegion = new Rect(displayRegion);
        final boolean isLeftRightSplit = mSplitScreen != null && mSplitScreen.isLeftRightSplit();
        final boolean inSplitScreen = mSplitScreen != null && mSplitScreen.isSplitScreenVisible();
        final float dividerWidth = mContext.getResources().getDimensionPixelSize(
                R.dimen.split_divider_bar_width);
        // We allow splitting if we are already in split-screen or the running task is a standard
        // task in fullscreen mode.
        final boolean allowSplit = inSplitScreen
                || (mSession.runningTaskActType == ACTIVITY_TYPE_STANDARD
                        && mSession.runningTaskWinMode == WINDOWING_MODE_FULLSCREEN);
        if (allowSplit) {
            if (enableFlexibleSplit()) {
                // TODO(b/349828130) get this from split screen controller, expose the SnapTarget object
                //  entirely and then pull out the SnapPosition
                @SplitScreenConstants.SnapPosition int snapPosition = SNAP_TO_2_50_50;
                final Rect startHitRegion = new Rect();
                final Rect endHitRegion = new Rect();
                if (!inSplitScreen) {
                    // Currently in fullscreen, split in half
                    final Rect startBounds = new Rect();
                    final Rect endBounds = new Rect();
                    mSplitScreen.getStageBounds(startBounds, endBounds);
                    startBounds.intersect(displayRegion);
                    endBounds.intersect(displayRegion);

                    if (isLeftRightSplit) {
                        displayRegion.splitVertically(startHitRegion, endHitRegion);
                    } else {
                        displayRegion.splitHorizontally(startHitRegion, endHitRegion);
                    }

                    mTargets.add(new Target(TYPE_SPLIT_LEFT, startHitRegion, startBounds,
                            SPLIT_INDEX_0));
                    mTargets.add(new Target(TYPE_SPLIT_RIGHT, endHitRegion, endBounds,
                            SPLIT_INDEX_1));
                } else {
                    // TODO(b/349828130), move this into init function and/or the insets updating
                    //  callback
                    DropTargetAnimSupplier supplier = null;
                    switch (snapPosition) {
                        case SNAP_TO_2_50_50:
                            supplier = new TwoFiftyFiftyTargetAnimator();
                        break;
                        case SplitScreenConstants.SNAP_TO_2_33_66:
                            break;
                        case SplitScreenConstants.SNAP_TO_2_66_33:
                            break;
                        case SplitScreenConstants.SNAP_TO_END_AND_DISMISS:
                            break;
                        case SplitScreenConstants.SNAP_TO_MINIMIZE:
                            break;
                        case SplitScreenConstants.SNAP_TO_NONE:
                            break;
                        case SplitScreenConstants.SNAP_TO_START_AND_DISMISS:
                            break;
                        default:
                    }

                    Pair<List<Target>, List<List<HoverAnimProps>>> targetsAnims =
                            supplier.getTargets(mSession.displayLayout,
                                    insets, isLeftRightSplit, mContext.getResources());
                    mTargets = new ArrayList<>(targetsAnims.getFirst());
                    mHoverAnimProps.put(SNAP_TO_2_50_50, targetsAnims.getSecond());
                    assert(mTargets.size() == targetsAnims.getSecond().size());
                    if (DEBUG_LAYOUT) {
                        for (List<HoverAnimProps> props : targetsAnims.getSecond()) {
                            StringBuilder sb = new StringBuilder();
                            for (HoverAnimProps hap : props) {
                                sb.append(hap).append("\n");
                            }
                            sb.append("\n");
                            Log.d(TAG, sb.toString());
                        }
                    }
                }
            } else {
                // Already split, allow replacing existing split task
                final Rect topOrLeftBounds = new Rect();
                final Rect bottomOrRightBounds = new Rect();
                mSplitScreen.getStageBounds(topOrLeftBounds, bottomOrRightBounds);
                topOrLeftBounds.intersect(displayRegion);
                bottomOrRightBounds.intersect(displayRegion);

                if (isLeftRightSplit) {
                    final Rect leftHitRegion = new Rect();
                    final Rect rightHitRegion = new Rect();

                    // If we have existing split regions use those bounds, otherwise split it 50/50
                    if (inSplitScreen) {
                        // The bounds of the existing split will have a divider bar, the hit region
                        // should include that space. Find the center of the divider bar:
                        float centerX = topOrLeftBounds.right + (dividerWidth / 2);
                        // Now set the hit regions using that center.
                        leftHitRegion.set(displayRegion);
                        leftHitRegion.right = (int) centerX;
                        rightHitRegion.set(displayRegion);
                        rightHitRegion.left = (int) centerX;
                    } else {
                        displayRegion.splitVertically(leftHitRegion, rightHitRegion);
                    }

                    mTargets.add(new Target(TYPE_SPLIT_LEFT, leftHitRegion, topOrLeftBounds,
                            SPLIT_INDEX_UNDEFINED));
                    mTargets.add(new Target(TYPE_SPLIT_RIGHT, rightHitRegion, bottomOrRightBounds,
                            SPLIT_INDEX_UNDEFINED));
                } else {
                    final Rect topHitRegion = new Rect();
                    final Rect bottomHitRegion = new Rect();

                    // If we have existing split regions use those bounds, otherwise split it 50/50
                    if (inSplitScreen) {
                        // The bounds of the existing split will have a divider bar, the hit region
                        // should include that space. Find the center of the divider bar:
                        float centerX = topOrLeftBounds.bottom + (dividerWidth / 2);
                        // Now set the hit regions using that center.
                        topHitRegion.set(displayRegion);
                        topHitRegion.bottom = (int) centerX;
                        bottomHitRegion.set(displayRegion);
                        bottomHitRegion.top = (int) centerX;
                    } else {
                        displayRegion.splitHorizontally(topHitRegion, bottomHitRegion);
                    }

                    mTargets.add(new Target(TYPE_SPLIT_TOP, topHitRegion, topOrLeftBounds,
                            SPLIT_INDEX_UNDEFINED));
                    mTargets.add(new Target(TYPE_SPLIT_BOTTOM, bottomHitRegion, bottomOrRightBounds,
                            SPLIT_INDEX_UNDEFINED));
                }
            }
        } else {
            // Split-screen not allowed, so only show the fullscreen target
            mTargets.add(new Target(TYPE_FULLSCREEN, fullscreenHitRegion, fullscreenDrawRegion, -1));
        }
        return mTargets;
    }

    /**
     * Returns the target at the given position based on the targets previously calculated.
     */
    @Nullable
    public Target getTargetAtLocation(int x, int y) {
        if (mDisallowHitRegion.contains(x, y)) {
            return null;
        }
        for (int i = mTargets.size() - 1; i >= 0; i--) {
            SplitDragPolicy.Target t = mTargets.get(i);
            if (enableFlexibleSplit() && mCurrentHoverTarget != null) {
                // If we're in flexible split, the targets themselves animate, so we have to rely
                // on the view's animated position for subsequent drag coordinates which we also
                // cache in HoverAnimProps.
                List<List<HoverAnimProps>> hoverAnimPropTargets =
                        mHoverAnimProps.get(mCurrentSnapPosition);
                for (HoverAnimProps animProps :
                        hoverAnimPropTargets.get(mCurrentHoverTarget.index)) {
                    if (animProps.getHoverRect() != null &&
                            animProps.getHoverRect().contains(x, y)) {
                        return animProps.getTarget();
                    }
                }

            }

            if (t.hitRegion.contains(x, y)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Handles the drop on a given {@param target}.  If a {@param hideTaskToken} is set, then the
     * handling of the drop will attempt to hide the given task as a part of the same window
     * container transaction if possible.
     */
    @VisibleForTesting
    public void onDropped(Target target, @Nullable WindowContainerToken hideTaskToken) {
        if (target == null || !mTargets.contains(target)) {
            return;
        }

        final boolean leftOrTop = target.type == TYPE_SPLIT_TOP || target.type == TYPE_SPLIT_LEFT;

        @SplitPosition int position = SPLIT_POSITION_UNDEFINED;
        if (target.type != TYPE_FULLSCREEN && mSplitScreen != null) {
            // Update launch options for the split side we are targeting.
            position = leftOrTop ? SPLIT_POSITION_TOP_OR_LEFT : SPLIT_POSITION_BOTTOM_OR_RIGHT;
            // Add some data for logging splitscreen once it is invoked
            mSplitScreen.onDroppedToSplit(position, mLoggerSessionId);
        }

        final Starter starter = target.type == TYPE_FULLSCREEN
                ? mFullscreenStarter
                : mSplitscreenStarter;
        if (mSession.appData != null) {
            launchApp(mSession, starter, position, hideTaskToken, target.index);
        } else {
            launchIntent(mSession, starter, position, hideTaskToken, target.index);
        }

        if (enableFlexibleSplit()) {
            reset();
        }
    }

    /**
     * Launches an app provided by SysUI.
     */
    private void launchApp(DragSession session, Starter starter, @SplitPosition int position,
            @Nullable WindowContainerToken hideTaskToken, @SplitIndex int splitIndex) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                "Launching app data at position=%d index=%d",
                position, splitIndex);
        final ClipDescription description = session.getClipDescription();
        final boolean isTask = description.hasMimeType(MIMETYPE_APPLICATION_TASK);
        final boolean isShortcut = description.hasMimeType(MIMETYPE_APPLICATION_SHORTCUT);
        final ActivityOptions baseActivityOpts = ActivityOptions.makeBasic();
        baseActivityOpts.setDisallowEnterPictureInPictureWhileLaunching(true);
        // Put BAL flags to avoid activity start aborted.
        baseActivityOpts.setPendingIntentBackgroundActivityStartMode(
                MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
        final Bundle opts = baseActivityOpts.toBundle();
        if (session.appData.hasExtra(EXTRA_ACTIVITY_OPTIONS)) {
            opts.putAll(session.appData.getBundleExtra(EXTRA_ACTIVITY_OPTIONS));
        }
        final UserHandle user = session.appData.getParcelableExtra(EXTRA_USER);

        if (isTask) {
            final int taskId = session.appData.getIntExtra(EXTRA_TASK_ID, INVALID_TASK_ID);
            starter.startTask(taskId, position, opts, hideTaskToken);
        } else if (isShortcut) {
            if (hideTaskToken != null) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                        "Can not hide task token with starting shortcut");
            }
            final String packageName = session.appData.getStringExtra(EXTRA_PACKAGE_NAME);
            final String id = session.appData.getStringExtra(EXTRA_SHORTCUT_ID);
            starter.startShortcut(packageName, id, position, opts, user);
        } else {
            final PendingIntent launchIntent =
                    session.appData.getParcelableExtra(EXTRA_PENDING_INTENT);
            if (Build.IS_DEBUGGABLE) {
                if (!user.equals(launchIntent.getCreatorUserHandle())) {
                    Log.e(TAG, "Expected app intent's EXTRA_USER to match pending intent user");
                }
            }
            starter.startIntent(launchIntent, user.getIdentifier(), null /* fillIntent */,
                    position, opts, hideTaskToken, splitIndex);
        }
    }

    /**
     * Launches an intent sender provided by an application.
     */
    private void launchIntent(DragSession session, Starter starter, @SplitPosition int position,
            @Nullable WindowContainerToken hideTaskToken, @SplitIndex int index) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "Launching intent at position=%d",
                position);
        final ActivityOptions baseActivityOpts = ActivityOptions.makeBasic();
        baseActivityOpts.setDisallowEnterPictureInPictureWhileLaunching(true);
        baseActivityOpts.setPendingIntentBackgroundActivityStartMode(
                MODE_BACKGROUND_ACTIVITY_START_DENIED);
        // TODO(b/255649902): Rework this so that SplitScreenController can always use the options
        // instead of a fillInIntent since it's assuming that the PendingIntent is mutable
        baseActivityOpts.setPendingIntentLaunchFlags(FLAG_ACTIVITY_NEW_TASK
                | FLAG_ACTIVITY_MULTIPLE_TASK);

        final Bundle opts = baseActivityOpts.toBundle();
        starter.startIntent(session.launchableIntent,
                session.launchableIntent.getCreatorUserHandle().getIdentifier(),
                null /* fillIntent */, position, opts, hideTaskToken, index);
    }

    @Override
    public void onHoveringOver(Target hoverTarget) {
        final boolean isLeftRightSplit = mSplitScreen != null && mSplitScreen.isLeftRightSplit();
        final boolean inSplitScreen = mSplitScreen != null && mSplitScreen.isSplitScreenVisible();
        if (!inSplitScreen) {
            // no need to animate for entering 50/50 split
            return;
        }

        mCurrentHoverTarget = hoverTarget;
        if (hoverTarget == null) {
            // Reset to default state
            BiConsumer<Target, View> biConsumer = new BiConsumer<Target, View>() {
                @Override
                public void accept(Target target, View view) {
                    // take into account left/right split
                    Animator transX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
                            target.drawRegion.left);
                    Animator transY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y,
                            target.drawRegion.top);
                    Animator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1);
                    Animator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1);
                    AnimatorSet as = new AnimatorSet();
                    as.play(transX);
                    as.play(transY);
                    as.play(scaleX);
                    as.play(scaleY);

                    as.start();
                }
            };
            mDragZoneAnimator.animateDragTargets(List.of(biConsumer));
            return;
        }

        // TODO(b/349828130) get this from split controller
        @SplitScreenConstants.SnapPosition int snapPosition = SNAP_TO_2_50_50;
        mCurrentSnapPosition = SNAP_TO_2_50_50;
        List<BiConsumer<Target, View>> animatingConsumers = new ArrayList<>();
        final List<List<HoverAnimProps>> hoverAnimProps = mHoverAnimProps.get(snapPosition);
        List<HoverAnimProps> animProps = hoverAnimProps.get(hoverTarget.index);
        // Expand start and push out the rest to the end
        BiConsumer<Target, View> biConsumer = new BiConsumer<>() {
            @Override
            public void accept(Target target, View view) {
                if (animProps.isEmpty() || animProps.size() < (target.index + 1)) {
                    return;
                }
                HoverAnimProps singleAnimProp = animProps.get(target.index);
                Animator transX = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
                        singleAnimProp.getTransX());
                Animator transY = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y,
                        singleAnimProp.getTransY());
                Animator scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X,
                        singleAnimProp.getScaleX());
                Animator scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y,
                        singleAnimProp.getScaleY());
                AnimatorSet as = new AnimatorSet();
                as.play(transX);
                as.play(transY);
                as.play(scaleX);
                as.play(scaleY);
                as.start();
            }
        };
        animatingConsumers.add(biConsumer);
        mDragZoneAnimator.animateDragTargets(animatingConsumers);
    }

    private void reset() {
        mCurrentHoverTarget = null;
        mCurrentSnapPosition = -1;
    }



    /**
     * Interface for actually committing the task launches.
     */
    public interface Starter {
        void startTask(int taskId, @SplitPosition int position, @Nullable Bundle options,
                @Nullable WindowContainerToken hideTaskToken);
        void startShortcut(String packageName, String shortcutId, @SplitPosition int position,
                @Nullable Bundle options, UserHandle user);
        void startIntent(PendingIntent intent, int userId, Intent fillInIntent,
                @SplitPosition int position, @Nullable Bundle options,
                @Nullable WindowContainerToken hideTaskToken, @SplitIndex int index);
        void enterSplitScreen(int taskId, boolean leftOrTop);

        /**
         * Exits splitscreen, with an associated exit trigger from the SplitscreenUIChanged proto
         * for logging.
         */
        void exitSplitScreen(int toTopTaskId, int exitTrigger);
    }

    /**
     * Default implementation of the starter which calls through the system services to launch the
     * tasks.
     */
    private static class DefaultStarter implements Starter {
        private final Context mContext;

        public DefaultStarter(Context context) {
            mContext = context;
        }

        @Override
        public void startTask(int taskId, int position, @Nullable Bundle options,
                @Nullable WindowContainerToken hideTaskToken) {
            if (hideTaskToken != null) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                        "Default starter does not support hide task token");
            }
            try {
                ActivityTaskManager.getService().startActivityFromRecents(taskId, options);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to launch task", e);
            }
        }

        @Override
        public void startShortcut(String packageName, String shortcutId, int position,
                @Nullable Bundle options, UserHandle user) {
            try {
                LauncherApps launcherApps =
                        mContext.getSystemService(LauncherApps.class);
                launcherApps.startShortcut(packageName, shortcutId, null /* sourceBounds */,
                        options, user);
            } catch (ActivityNotFoundException e) {
                Slog.e(TAG, "Failed to launch shortcut", e);
            }
        }

        @Override
        public void startIntent(PendingIntent intent, int userId, @Nullable Intent fillInIntent,
                int position, @Nullable Bundle options,
                @Nullable WindowContainerToken hideTaskToken, @SplitIndex int index) {
            if (hideTaskToken != null) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                        "Default starter does not support hide task token");
            }
            try {
                intent.send(mContext, 0, fillInIntent, null, null, null, options);
            } catch (PendingIntent.CanceledException e) {
                Slog.e(TAG, "Failed to launch activity", e);
            }
        }

        @Override
        public void enterSplitScreen(int taskId, boolean leftOrTop) {
            throw new UnsupportedOperationException("enterSplitScreen not implemented by starter");
        }

        @Override
        public void exitSplitScreen(int toTopTaskId, int exitTrigger) {
            throw new UnsupportedOperationException("exitSplitScreen not implemented by starter");
        }
    }

    /**
     * Represents a drop target.
     * TODO(b/349828130): Move this into {@link DropTarget}
     */
    public static class Target {
        static final int TYPE_FULLSCREEN = 0;
        public static final int TYPE_SPLIT_LEFT = 1;
        static final int TYPE_SPLIT_TOP = 2;
        static final int TYPE_SPLIT_RIGHT = 3;
        static final int TYPE_SPLIT_BOTTOM = 4;
        @IntDef(value = {
                TYPE_FULLSCREEN,
                TYPE_SPLIT_LEFT,
                TYPE_SPLIT_TOP,
                TYPE_SPLIT_RIGHT,
                TYPE_SPLIT_BOTTOM
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface Type{}

        final @Type int type;

        // The actual hit region for this region
        final Rect hitRegion;
        // The approximate visual region for where the task will start
        final Rect drawRegion;
        @SplitIndex int index;

        /**
         * @param index 0-indexed, represents which position of drop target this object represents,
         *              0 to N for left to right, top to bottom
         */
        public Target(@Type int t, Rect hit, Rect draw, @SplitIndex int index) {
            type = t;
            hitRegion = hit;
            drawRegion = draw;
            this.index = index;
        }

        @Override
        public String toString() {
            return "Target {type=" + type + " hit=" + hitRegion + " draw=" + drawRegion
                    + " index=" + index + "}";
        }
    }
}
