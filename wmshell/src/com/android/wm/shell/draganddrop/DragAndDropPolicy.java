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

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
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

import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.draganddrop.DragAndDropConstants.EXTRA_DISALLOW_HIT_REGION;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_FULLSCREEN;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_SPLIT_BOTTOM;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_SPLIT_LEFT;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_SPLIT_RIGHT;
import static com.android.wm.shell.draganddrop.DragAndDropPolicy.Target.TYPE_SPLIT_TOP;

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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.InstanceId;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.common.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.splitscreen.SplitScreenController;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;

/**
 * The policy for handling drag and drop operations to shell.
 */
public class DragAndDropPolicy {

    private static final String TAG = DragAndDropPolicy.class.getSimpleName();

    private final Context mContext;
    private final Starter mStarter;
    private final SplitScreenController mSplitScreen;
    private final ArrayList<DragAndDropPolicy.Target> mTargets = new ArrayList<>();
    private final RectF mDisallowHitRegion = new RectF();

    private InstanceId mLoggerSessionId;
    private DragSession mSession;

    public DragAndDropPolicy(Context context, SplitScreenController splitScreen) {
        this(context, splitScreen, new DefaultStarter(context));
    }

    @VisibleForTesting
    DragAndDropPolicy(Context context, SplitScreenController splitScreen, Starter starter) {
        mContext = context;
        mSplitScreen = splitScreen;
        mStarter = mSplitScreen != null ? mSplitScreen : starter;
    }

    /**
     * Starts a new drag session with the given initial drag data.
     */
    void start(DragSession session, InstanceId loggerSessionId) {
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
    int getNumTargets() {
        return mTargets.size();
    }

    /**
     * Returns the target's regions based on the current state of the device and display.
     */
    @NonNull
    ArrayList<Target> getTargets(Insets insets) {
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

                mTargets.add(new Target(TYPE_SPLIT_LEFT, leftHitRegion, topOrLeftBounds));
                mTargets.add(new Target(TYPE_SPLIT_RIGHT, rightHitRegion, bottomOrRightBounds));

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

                mTargets.add(new Target(TYPE_SPLIT_TOP, topHitRegion, topOrLeftBounds));
                mTargets.add(new Target(TYPE_SPLIT_BOTTOM, bottomHitRegion, bottomOrRightBounds));
            }
        } else {
            // Split-screen not allowed, so only show the fullscreen target
            mTargets.add(new Target(TYPE_FULLSCREEN, fullscreenHitRegion, fullscreenDrawRegion));
        }
        return mTargets;
    }

    /**
     * Returns the target at the given position based on the targets previously calculated.
     */
    @Nullable
    Target getTargetAtLocation(int x, int y) {
        if (mDisallowHitRegion.contains(x, y)) {
            return null;
        }
        for (int i = mTargets.size() - 1; i >= 0; i--) {
            DragAndDropPolicy.Target t = mTargets.get(i);
            if (t.hitRegion.contains(x, y)) {
                return t;
            }
        }
        return null;
    }

    @VisibleForTesting
    void handleDrop(Target target) {
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

        if (mSession.appData != null) {
            launchApp(mSession, position);
        } else {
            launchIntent(mSession, position);
        }
    }

    /**
     * Launches an app provided by SysUI.
     */
    private void launchApp(DragSession session, @SplitPosition int position) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "Launching app data at position=%d",
                position);
        final ClipDescription description = session.getClipDescription();
        final boolean isTask = description.hasMimeType(MIMETYPE_APPLICATION_TASK);
        final boolean isShortcut = description.hasMimeType(MIMETYPE_APPLICATION_SHORTCUT);
        final ActivityOptions baseActivityOpts = ActivityOptions.makeBasic();
        baseActivityOpts.setDisallowEnterPictureInPictureWhileLaunching(true);
        // Put BAL flags to avoid activity start aborted.
//        baseActivityOpts.setPendingIntentBackgroundActivityStartMode(
//                MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
//        baseActivityOpts.setPendingIntentBackgroundActivityLaunchAllowedByPermission(true);
        final Bundle opts = baseActivityOpts.toBundle();
        if (session.appData.hasExtra(EXTRA_ACTIVITY_OPTIONS)) {
            opts.putAll(session.appData.getBundleExtra(EXTRA_ACTIVITY_OPTIONS));
        }
        final UserHandle user = session.appData.getParcelableExtra(EXTRA_USER);

        if (isTask) {
            final int taskId = session.appData.getIntExtra(EXTRA_TASK_ID, INVALID_TASK_ID);
            mStarter.startTask(taskId, position, opts);
        } else if (isShortcut) {
            final String packageName = session.appData.getStringExtra(EXTRA_PACKAGE_NAME);
            final String id = session.appData.getStringExtra(EXTRA_SHORTCUT_ID);
            mStarter.startShortcut(packageName, id, position, opts, user);
        } else {
            final PendingIntent launchIntent =
                    session.appData.getParcelableExtra(EXTRA_PENDING_INTENT);
            if (Build.IS_DEBUGGABLE) {
                if (!user.equals(launchIntent.getCreatorUserHandle())) {
                    Log.e(TAG, "Expected app intent's EXTRA_USER to match pending intent user");
                }
            }
            mStarter.startIntent(launchIntent, user.getIdentifier(), null /* fillIntent */,
                    position, opts);
        }
    }

    /**
     * Launches an intent sender provided by an application.
     */
    private void launchIntent(DragSession session, @SplitPosition int position) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "Launching intent at position=%d",
                position);
        final ActivityOptions baseActivityOpts = ActivityOptions.makeBasic();
//        baseActivityOpts.setDisallowEnterPictureInPictureWhileLaunching(true);
//        baseActivityOpts.setPendingIntentBackgroundActivityStartMode(
//                MODE_BACKGROUND_ACTIVITY_START_DENIED);
        // TODO(b/255649902): Rework this so that SplitScreenController can always use the options
        // instead of a fillInIntent since it's assuming that the PendingIntent is mutable
//        baseActivityOpts.setPendingIntentLaunchFlags(FLAG_ACTIVITY_NEW_TASK
//                | FLAG_ACTIVITY_MULTIPLE_TASK);

        final Bundle opts = baseActivityOpts.toBundle();
        mStarter.startIntent(session.launchableIntent,
                session.launchableIntent.getCreatorUserHandle().getIdentifier(),
                null /* fillIntent */, position, opts);
    }

    /**
     * Interface for actually committing the task launches.
     */
    public interface Starter {
        void startTask(int taskId, @SplitPosition int position, @Nullable Bundle options);
        void startShortcut(String packageName, String shortcutId, @SplitPosition int position,
                @Nullable Bundle options, UserHandle user);
        void startIntent(PendingIntent intent, int userId, Intent fillInIntent,
                @SplitPosition int position, @Nullable Bundle options);
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
        public void startTask(int taskId, int position, @Nullable Bundle options) {
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
                int position, @Nullable Bundle options) {
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
     */
    static class Target {
        static final int TYPE_FULLSCREEN = 0;
        static final int TYPE_SPLIT_LEFT = 1;
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

        public Target(@Type int t, Rect hit, Rect draw) {
            type = t;
            hitRegion = hit;
            drawRegion = draw;
        }

        @Override
        public String toString() {
            return "Target {hit=" + hitRegion + " draw=" + drawRegion + "}";
        }
    }
}
