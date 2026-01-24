/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.pip2.phone;

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.view.Display.DEFAULT_DISPLAY;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.DesktopExperienceFlags;
import android.window.DisplayAreaInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.BinderThread;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.Preconditions;
import com.android.wm.shell.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayChangeController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.ImeListener;
import com.android.wm.shell.common.NavigationBarsListener;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SingleInstanceRemoteListener;
import com.android.wm.shell.common.TabletopModeController;
import com.android.wm.shell.common.TaskStackListenerCallback;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.IPip;
import com.android.wm.shell.common.pip.IPipAnimationListener;
import com.android.wm.shell.common.pip.PipAppOpsListener;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipKeepClearAlgorithmInterface;
import com.android.wm.shell.common.pip.PipMediaController;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.pip.Pip;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages the picture-in-picture (PIP) UI and states for Phones.
 */
public class PipController implements ConfigurationChangeListener,
        PipTransitionState.PipTransitionStateChangedListener,
        DisplayController.OnDisplaysChangedListener,
        DisplayChangeController.OnDisplayChangingListener, RemoteCallable<PipController>,
        PipDisplayLayoutState.DisplayIdListener {
    private static final String TAG = PipController.class.getSimpleName();
    private static final String SWIPE_TO_PIP_APP_BOUNDS = "pip_app_bounds";
    private static final String SWIPE_TO_PIP_OVERLAY = "swipe_to_pip_overlay";
    private static final String DISPLAY_CHANGE_PIP_BOUNDS_UPDATE =
            "display_change_pip_bounds_update";

    private Context mContext;
    private final ShellCommandHandler mShellCommandHandler;
    private final ShellController mShellController;
    private final DisplayController mDisplayController;
    private final DisplayInsetsController mDisplayInsetsController;
    private final PipBoundsState mPipBoundsState;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final PipScheduler mPipScheduler;
    private final TaskStackListenerImpl mTaskStackListener;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final PipTransitionState mPipTransitionState;
    private final PipTouchHandler mPipTouchHandler;
    private final PipAppOpsListener mPipAppOpsListener;
    private final PhonePipMenuController mPipMenuController;
    private final TabletopModeController mTabletopModeController;
    private final PipKeepClearAlgorithmInterface mPipKeepClearAlgorithm;
    private final PipUiEventLogger mPipUiEventLogger;
    private final ShellExecutor mMainExecutor;
    private final PipImpl mImpl;
    private final List<Consumer<Boolean>> mOnIsInPipStateChangedListeners = new ArrayList<>();
    private final PipMediaController mMediaController;

    // Wrapper for making Binder calls into PiP animation listener hosted in launcher's Recents.
    @Nullable private PipAnimationListener mPipRecentsAnimationListener;

    private final PipSurfaceTransactionHelper.SurfaceControlTransactionFactory
            mSurfaceControlTransactionFactory;
    private final PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;

    private boolean mWaitingToPlayDisplayChangeBoundsUpdate;

    @VisibleForTesting
    interface PipAnimationListener {
        /**
         * Notifies the listener that the Pip animation is started.
         */
        void onPipAnimationStarted();

        /**
         * Notifies the listener about PiP resource dimensions changed.
         * Listener can expect an immediate callback the first time they attach.
         *
         * @param cornerRadius the pixel value of the corner radius, zero means it's disabled.
         * @param shadowRadius the pixel value of the shadow radius, zero means it's disabled.
         */
        void onPipResourceDimensionsChanged(int cornerRadius, int shadowRadius);

        /**
         * Notifies the listener that user leaves PiP by tapping on the expand button.
         */
        void onExpandPip();
    }

    private PipController(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            PipBoundsState pipBoundsState,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            PipDisplayLayoutState pipDisplayLayoutState,
            PipScheduler pipScheduler,
            TaskStackListenerImpl taskStackListener,
            ShellTaskOrganizer shellTaskOrganizer,
            PipTransitionState pipTransitionState,
            PipTouchHandler pipTouchHandler,
            PipAppOpsListener pipAppOpsListener,
            PhonePipMenuController pipMenuController,
            PipUiEventLogger pipUiEventLogger,
            PipMediaController pipMediaController,
            TabletopModeController tabletopModeController,
            PipKeepClearAlgorithmInterface pipKeepClearAlgorithm,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            ShellExecutor mainExecutor) {
        mContext = context;
        mShellCommandHandler = shellCommandHandler;
        mShellController = shellController;
        mDisplayController = displayController;
        mDisplayInsetsController = displayInsetsController;
        mPipBoundsState = pipBoundsState;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipDisplayLayoutState.addDisplayIdListener(this);
        mPipScheduler = pipScheduler;
        mTaskStackListener = taskStackListener;
        mShellTaskOrganizer = shellTaskOrganizer;
        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this);
        mPipTouchHandler = pipTouchHandler;
        mPipAppOpsListener = pipAppOpsListener;
        mPipMenuController = pipMenuController;
        mPipUiEventLogger = pipUiEventLogger;
        mMediaController = pipMediaController;
        mTabletopModeController = tabletopModeController;
        mPipKeepClearAlgorithm = pipKeepClearAlgorithm;
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mMainExecutor = mainExecutor;
        mImpl = new PipImpl();
        mSurfaceControlTransactionFactory =
                new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();

        if (PipFlags.isPip2ExperimentEnabled()) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    /**
     * Instantiates {@link PipController}, returns {@code null} if the feature not supported.
     */
    public static PipController create(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            PipBoundsState pipBoundsState,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            PipDisplayLayoutState pipDisplayLayoutState,
            PipScheduler pipScheduler,
            TaskStackListenerImpl taskStackListener,
            ShellTaskOrganizer shellTaskOrganizer,
            PipTransitionState pipTransitionState,
            PipTouchHandler pipTouchHandler,
            PipAppOpsListener pipAppOpsListener,
            PhonePipMenuController pipMenuController,
            PipUiEventLogger pipUiEventLogger,
            PipMediaController pipMediaController,
            TabletopModeController tabletopModeController,
            PipKeepClearAlgorithmInterface pipKeepClearAlgorithm,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            ShellExecutor mainExecutor) {
        if (!context.getPackageManager().hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Device doesn't support Pip feature", TAG);
            return null;
        }
        return new PipController(context, shellInit, shellCommandHandler, shellController,
                displayController, displayInsetsController, pipBoundsState, pipBoundsAlgorithm,
                pipDisplayLayoutState, pipScheduler, taskStackListener, shellTaskOrganizer,
                pipTransitionState, pipTouchHandler, pipAppOpsListener, pipMenuController,
                pipUiEventLogger, pipMediaController, tabletopModeController,
                pipKeepClearAlgorithm, pipSurfaceTransactionHelper, mainExecutor);
    }

    public PipImpl getPipImpl() {
        return mImpl;
    }

    private void onInit() {
        mShellCommandHandler.addDumpCallback(this::dump, this);
        // Ensure that we have the display info in case we get calls to update the bounds before the
        // listener calls back
        mPipDisplayLayoutState.setDisplayId(mContext.getDisplayId());
        DisplayLayout layout = new DisplayLayout(mContext, mContext.getDisplay());
        mPipDisplayLayoutState.setDisplayLayout(layout);

        mDisplayController.addDisplayChangingController(this);
        mDisplayController.addDisplayWindowListener(this);
        mDisplayInsetsController.addInsetsChangedListener(mPipDisplayLayoutState.getDisplayId(),
                new ImeListener(mDisplayController, mPipDisplayLayoutState.getDisplayId()) {
                    @Override
                    public void onImeVisibilityChanged(boolean imeVisible, int imeHeight) {
                        mPipTouchHandler.onImeVisibilityChanged(imeVisible, imeHeight);
                    }
                });
        mDisplayInsetsController.addInsetsChangedListener(mPipDisplayLayoutState.getDisplayId(),
                new NavigationBarsListener(mDisplayController,
                        mPipDisplayLayoutState.getDisplayId()) {
                    @Override
                    protected void onNavigationBarsVisibilityChanged(
                            @NonNull Insets insets) {
                        mPipDisplayLayoutState.setNavigationBarsInsets(insets);
                    }
                });

        // Allow other outside processes to bind to PiP controller using the key below.
        mShellController.addExternalInterface(IPip.DESCRIPTOR,
                this::createExternalInterface, this);
        mShellController.addConfigurationChangeListener(this);

        mTaskStackListener.addListener(new TaskStackListenerCallback() {
            @Override
            public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                    boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "onActivityRestartAttempt: topActivity=%s, wasVisible=%b",
                        task.topActivity, wasVisible);
                if (task.getWindowingMode() != WINDOWING_MODE_PINNED || !wasVisible) {
                    return;
                }
                mPipScheduler.scheduleExitPipViaExpand();
            }
        });

        mTabletopModeController.registerOnTabletopModeChangedListener((isInTabletopMode) -> {
            if (!isInTabletopMode) {
                mPipBoundsState.setNamedUnrestrictedKeepClearArea(
                        PipBoundsState.NAMED_KCA_TABLETOP_MODE, null);
                return;
            }

            // To prepare for the entry bounds.
            final Rect displayBounds = mPipBoundsState.getDisplayBounds();
            if (mTabletopModeController.getPreferredHalfInTabletopMode()
                    == TabletopModeController.PREFERRED_TABLETOP_HALF_TOP) {
                // Prefer top, avoid the bottom half of the display.
                mPipBoundsState.setNamedUnrestrictedKeepClearArea(
                        PipBoundsState.NAMED_KCA_TABLETOP_MODE, new Rect(
                                displayBounds.left, displayBounds.centerY(),
                                displayBounds.right, displayBounds.bottom));
            } else {
                // Prefer bottom, avoid the top half of the display.
                mPipBoundsState.setNamedUnrestrictedKeepClearArea(
                        PipBoundsState.NAMED_KCA_TABLETOP_MODE, new Rect(
                                displayBounds.left, displayBounds.top,
                                displayBounds.right, displayBounds.centerY()));
            }

            // Try to move the PiP window if we have entered PiP mode.
            if (mPipTransitionState.isInPip()) {
                final Rect pipBounds = mPipBoundsState.getBounds();
                final Point edgeInsets = mPipDisplayLayoutState.getScreenEdgeInsets();
                if ((pipBounds.height() + 2 * edgeInsets.y) > (displayBounds.height() / 2)) {
                    // PiP bounds is too big to fit either half, bail early.
                    return;
                }
                Rect destBounds = mPipKeepClearAlgorithm.adjust(mPipBoundsState,
                        mPipBoundsAlgorithm);
                // only move if the bounds are actually different
                if (!destBounds.equals(mPipBoundsState.getBounds())) {
                    ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "Move PiP from %s to %s on tabletop mode change",
                            mPipBoundsState.getBounds(), destBounds);
                    mPipTouchHandler.getMotionHelper().moveToBounds(destBounds);
                }
            }
        });

        mPipAppOpsListener.setCallback(mPipTouchHandler.getMotionHelper());
    }

    private ExternalInterfaceBinder createExternalInterface() {
        return new IPipImpl(this);
    }

    //
    // RemoteCallable implementations
    //

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mMainExecutor;
    }

    //
    // ConfigurationChangeListener implementations
    //

    @Override
    public void onConfigurationChanged(Configuration newConfiguration) {
        mPipDisplayLayoutState.onConfigurationChanged();
        mPipTouchHandler.onConfigurationChanged();
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        onPipResourceDimensionsChanged();
    }

    @Override
    public void onThemeChanged() {
        setDisplayLayout(new DisplayLayout(mContext, mContext.getDisplay()));
        if (Flags.enablePipBoxShadows()) {
            if (mPipTransitionState.isInPip()) {
                SurfaceControl pipLeash = mPipTransitionState.getPinnedTaskLeash();
                mPipSurfaceTransactionHelper.onThemeChanged(mContext);
                SurfaceControl.Transaction tx = mSurfaceControlTransactionFactory.getTransaction();
                mPipSurfaceTransactionHelper.shadow(tx, pipLeash, true /* applyShadowRadius */);
                tx.apply();
            }
        }
    }

    //
    // DisplayController.OnDisplaysChangedListener and
    // DisplayChangeController.OnDisplayChangingListener implementations
    //

    @Override
    public void onDisplayAdded(int displayId) {
        if (displayId != mPipDisplayLayoutState.getDisplayId()) {
            return;
        }
        setDisplayLayout(mDisplayController.getDisplayLayout(displayId));
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        // If PiP was active on an external display that is removed, clean up states and set
        // {@link PipDisplayLayoutState} to DEFAULT_DISPLAY.
        if (DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_PIP.isTrue()
                && mPipTransitionState.isInPip()
                && displayId == mPipDisplayLayoutState.getDisplayId()
                && displayId != DEFAULT_DISPLAY) {
            mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
            mPipTransitionState.setState(PipTransitionState.EXITED_PIP);

            mPipDisplayLayoutState.setDisplayId(DEFAULT_DISPLAY);
            mPipDisplayLayoutState.setDisplayLayout(
                    mDisplayController.getDisplayLayout(DEFAULT_DISPLAY));
        }
    }

    /**
     * A callback for any observed transition that contains a display change in its
     * {@link android.window.TransitionRequestInfo},
     */
    @Override
    public void onDisplayChange(int displayId, int fromRotation, int toRotation,
            @Nullable DisplayAreaInfo newDisplayAreaInfo, WindowContainerTransaction t) {
        if (displayId != mPipDisplayLayoutState.getDisplayId()) {
            return;
        }
        final float snapFraction = mPipBoundsAlgorithm.getSnapFraction(mPipBoundsState.getBounds());

        // Update the display layout caches even if we are not in PiP.
        setDisplayLayout(mDisplayController.getDisplayLayout(displayId));
        if (toRotation != ROTATION_UNDEFINED) {
            // Make sure we rotate to final rotation ourselves in case display change is coming
            // from the remote rotation as a part of an already collecting transition.
            mPipDisplayLayoutState.rotateTo(toRotation);
        }

        if (!shouldUpdatePipStateOnDisplayChange()) {
            // Skip the PiP-relevant updates if we aren't in a valid PiP state.
            if (mPipTransitionState.isInFixedRotation()) {
                ProtoLog.e(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                        "Fixed rotation flag shouldn't be set while in an invalid PiP state");
            }
            return;
        }

        mPipMenuController.hideMenu();

        if (mPipTransitionState.isInFixedRotation()) {
            // Do not change the bounds when in fixed rotation, but do update the movement bounds
            // based on the current bounds state and potentially new display layout.
            mPipTouchHandler.updateMovementBounds();
            mPipTransitionState.setInFixedRotation(false);
        } else {
            final float boundsScale = mPipBoundsState.getBoundsScale();
            // Before calculating the PiP bounds, the PiP minimum and maximum sizes
            // need to be recalculated for the current display.
            mPipBoundsState.updateMinMaxSize(mPipBoundsState.getAspectRatio());
            Rect toBounds = new Rect(0, 0,
                    (int) Math.ceil(mPipBoundsState.getMaxSize().x * boundsScale),
                    (int) Math.ceil(mPipBoundsState.getMaxSize().y * boundsScale));
            // Update the caches to reflect the new display layout in the movement bounds;
            // temporarily update bounds to be at the top left for the movement bounds calculation.
            mPipBoundsState.setBounds(toBounds);
            mPipTouchHandler.updateMovementBounds();
            // The policy is to keep PiP snap fraction invariant.
            mPipBoundsAlgorithm.applySnapFraction(toBounds, snapFraction);
            mPipBoundsState.setBounds(toBounds);
            mPipTouchHandler.setUserResizeBounds(toBounds);
        }
        if (mPipTransitionState.getPipTaskToken() == null) {
            Log.d(TAG, "PipController.onDisplayChange no PiP task token"
                    + " state=" + mPipTransitionState.getState());
            mPipTransitionState.setOnIdlePipTransitionStateRunnable(() -> {
                final Bundle extra = new Bundle();
                extra.putBoolean(DISPLAY_CHANGE_PIP_BOUNDS_UPDATE, true);
                mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
            });
        } else {
            mPipTransitionState.setIsPipBoundsChangingWithDisplay(true);
            t.setBounds(mPipTransitionState.getPipTaskToken(), mPipBoundsState.getBounds());
        }
        // Update the size spec in PipBoundsState afterwards.
        mPipBoundsState.updateMinMaxSize(mPipBoundsState.getAspectRatio());
    }

    private void setDisplayLayout(DisplayLayout layout) {
        mPipDisplayLayoutState.setDisplayLayout(layout);
    }

    private boolean shouldUpdatePipStateOnDisplayChange() {
        // We should at least update internal PiP state, such as PiP bounds state or movement bounds
        // if we are either in PiP or about to enter PiP.
        return mPipTransitionState.isInPip()
                || mPipTransitionState.getState() == PipTransitionState.ENTERING_PIP
                || mPipTransitionState.getState() == PipTransitionState.SCHEDULED_ENTER_PIP;
    }

    //
    // IPip Binder stub helpers
    //

    private Rect getSwipePipToHomeBounds(ComponentName componentName, ActivityInfo activityInfo,
            int displayId, PictureInPictureParams pictureInPictureParams,
            int launcherRotation, Rect hotseatKeepClearArea) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "getSwipePipToHomeBounds: %s", componentName);

        // If PiP is enabled on Connected Displays, update PipDisplayLayoutState to have the correct
        // display info that PiP is entering in.
        if (DesktopExperienceFlags.ENABLE_CONNECTED_DISPLAYS_PIP.isTrue()) {
            final DisplayLayout displayLayout = mDisplayController.getDisplayLayout(displayId);
            if (displayLayout != null) {
                mPipDisplayLayoutState.setDisplayId(displayId);
                mPipDisplayLayoutState.setDisplayLayout(displayLayout);
            }
        }

        // Preemptively add the keep clear area for Hotseat, so that it is taken into account
        // when calculating the entry destination bounds of PiP window.
        mPipBoundsState.setNamedUnrestrictedKeepClearArea(
                PipBoundsState.NAMED_KCA_LAUNCHER_SHELF, hotseatKeepClearArea);

        // Set the display layout rotation early to calculate final orientation bounds that
        // the animator expects, this will also be used to detect the fixed rotation when
        // Shell resolves the type of the animation we are undergoing.
        mPipDisplayLayoutState.rotateTo(launcherRotation);

        mPipBoundsState.setBoundsStateForEntry(componentName, activityInfo, pictureInPictureParams,
                mPipBoundsAlgorithm);

        // Update the size spec in case aspect ratio is invariant, but display has changed
        // since the last PiP session, or this is the first PiP session altogether.
        mPipBoundsState.updateMinMaxSize(mPipBoundsState.getAspectRatio());
        return mPipBoundsAlgorithm.getEntryDestinationBounds();
    }

    private void onSwipePipToHomeAnimationStart(int taskId, ComponentName componentName,
            Rect destinationBounds, SurfaceControl overlay, Rect appBounds,
            Rect sourceRectHint) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "onSwipePipToHomeAnimationStart: %s", componentName);
        Bundle extra = new Bundle();
        extra.putParcelable(SWIPE_TO_PIP_OVERLAY, overlay);
        extra.putParcelable(SWIPE_TO_PIP_APP_BOUNDS, appBounds);
        mPipTransitionState.setState(PipTransitionState.SWIPING_TO_PIP, extra);
        if (overlay != null) {
            // Shell transitions might use a root animation leash, which will be removed when
            // the Recents transition is finished. Launcher attaches the overlay leash to this
            // animation target leash; thus, we need to reparent it to the actual Task surface now.
            // PipTransition is responsible to fade it out and cleanup when finishing the enter PIP
            // transition.
            SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
            mShellTaskOrganizer.reparentChildSurfaceToTask(taskId, overlay, tx);
            tx.setLayer(overlay, Integer.MAX_VALUE);
            tx.apply();
        }
        if (mPipRecentsAnimationListener != null) {
            mPipRecentsAnimationListener.onPipAnimationStarted();
        }
    }

    private void setLauncherKeepClearAreaHeight(boolean visible, int height) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "setLauncherKeepClearAreaHeight: visible=%b, height=%d", visible, height);
        mPipTransitionState.setOnIdlePipTransitionStateRunnable(() -> {
            if (visible) {
                Rect rect = new Rect(
                        0, mPipDisplayLayoutState.getDisplayBounds().bottom - height,
                        mPipDisplayLayoutState.getDisplayBounds().right,
                        mPipDisplayLayoutState.getDisplayBounds().bottom);
                mPipBoundsState.setNamedUnrestrictedKeepClearArea(
                        PipBoundsState.NAMED_KCA_LAUNCHER_SHELF, rect);
            } else {
                mPipBoundsState.setNamedUnrestrictedKeepClearArea(
                        PipBoundsState.NAMED_KCA_LAUNCHER_SHELF, null);
            }
            mPipTouchHandler.onShelfVisibilityChanged(visible, height);
        });
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.SWIPING_TO_PIP:
                Preconditions.checkState(extra != null,
                        "No extra bundle for " + mPipTransitionState);

                SurfaceControl overlay = extra.getParcelable(
                        SWIPE_TO_PIP_OVERLAY, SurfaceControl.class);
                Rect appBounds = extra.getParcelable(
                        SWIPE_TO_PIP_APP_BOUNDS, Rect.class);

                Preconditions.checkState(appBounds != null,
                        "App bounds can't be null for " + mPipTransitionState);
                mPipTransitionState.setSwipePipToHomeState(overlay, appBounds);
                break;
            case PipTransitionState.ENTERED_PIP:
                final TaskInfo taskInfo = mPipTransitionState.getPipTaskInfo();
                if (taskInfo != null && taskInfo.topActivity != null) {
                    mPipAppOpsListener.onActivityPinned(taskInfo.topActivity.getPackageName());
                    mPipUiEventLogger.setTaskInfo(taskInfo);
                    mMediaController.onActivityPinned();
                }
                if (mPipTransitionState.isInSwipePipToHomeTransition()) {
                    mPipUiEventLogger.log(
                            PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_AUTO_ENTER);
                    mPipTransitionState.resetSwipePipToHomeState();
                } else if (PipUtils.isContentPip(taskInfo)) {
                    mPipUiEventLogger.log(
                            PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_ENTER_CONTENT_PIP);
                } else {
                    mPipUiEventLogger.log(PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_ENTER);
                }
                for (Consumer<Boolean> listener : mOnIsInPipStateChangedListeners) {
                    listener.accept(true /* inPip */);
                }
                break;
            case PipTransitionState.EXITED_PIP:
                mPipAppOpsListener.onActivityUnpinned();
                mPipUiEventLogger.setTaskInfo(null);
                for (Consumer<Boolean> listener : mOnIsInPipStateChangedListeners) {
                    listener.accept(false /* inPip */);
                }
                break;
            case PipTransitionState.SCHEDULED_BOUNDS_CHANGE:
                mWaitingToPlayDisplayChangeBoundsUpdate =
                        extra.getBoolean(DISPLAY_CHANGE_PIP_BOUNDS_UPDATE);
                if (mWaitingToPlayDisplayChangeBoundsUpdate) {
                    // If we reach this point, it means display change did not send through a WCT to
                    // update the pinned task bounds in Core. Instead, the local Shell-side
                    // PiP-relevant bounds state and movement bounds were updated.
                    // So schedule a jumpcut animation to those bounds now.
                    mPipScheduler.scheduleAnimateResizePip(mPipBoundsState.getBounds());
                }
                break;
            case PipTransitionState.CHANGING_PIP_BOUNDS:
                if (!mWaitingToPlayDisplayChangeBoundsUpdate) {
                    break;
                }
                mWaitingToPlayDisplayChangeBoundsUpdate = false;
                final SurfaceControl.Transaction startTx = extra.getParcelable(
                        PipTransition.PIP_START_TX, SurfaceControl.Transaction.class);
                final SurfaceControl.Transaction finishTx = extra.getParcelable(
                        PipTransition.PIP_FINISH_TX, SurfaceControl.Transaction.class);
                final Rect destinationBounds = extra.getParcelable(
                        PipTransition.PIP_DESTINATION_BOUNDS, Rect.class);
                handleJumpcutBoundsUpdate(startTx, finishTx, destinationBounds);
                break;
        }
    }

    private void handleJumpcutBoundsUpdate(SurfaceControl.Transaction startTx,
            SurfaceControl.Transaction finishTx, Rect destinationBounds) {
        SurfaceControl pipLeash = mPipTransitionState.getPinnedTaskLeash();

        startTx.merge(finishTx);
        startTx.setPosition(pipLeash, destinationBounds.left, destinationBounds.top);
        mPipSurfaceTransactionHelper.round(startTx, pipLeash, true /* applyCornerRadius */)
                .shadow(startTx, pipLeash, true /* applyShadowRadius */);
        mPipSurfaceTransactionHelper.round(finishTx, pipLeash, true /* applyCornerRadius */)
                .shadow(finishTx, pipLeash, true /* applyShadowRadius */);
        startTx.apply();

        // Signal that the transition is done - should update transition state by default.
        mPipScheduler.scheduleFinishPipBoundsChange(destinationBounds);
    }

    //
    // IPipAnimationListener Binder proxy helpers
    //

    private void setPipRecentsAnimationListener(PipAnimationListener pipAnimationListener) {
        mPipRecentsAnimationListener = pipAnimationListener;
        onPipResourceDimensionsChanged();
    }

    private void onPipResourceDimensionsChanged() {
        if (mPipRecentsAnimationListener != null) {
            mPipRecentsAnimationListener.onPipResourceDimensionsChanged(
                    mContext.getResources().getDimensionPixelSize(R.dimen.pip_corner_radius),
                    mContext.getResources().getDimensionPixelSize(R.dimen.pip_shadow_radius));
        }
    }

    @Override
    public void onDisplayIdChanged(@NonNull Context context) {
        mContext = context;
        onPipResourceDimensionsChanged();
    }

    private void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = "  ";
        pw.println(TAG);
        PipUtils.dump(pw, innerPrefix);
        mPipBoundsAlgorithm.dump(pw, innerPrefix);
        mPipBoundsState.dump(pw, innerPrefix);
        mPipDisplayLayoutState.dump(pw, innerPrefix);
        mPipTransitionState.dump(pw, innerPrefix);
    }

    private void addOnIsInPipStateChangedListener(@NonNull Consumer<Boolean> callback) {
        if (callback != null) {
            mOnIsInPipStateChangedListeners.add(callback);
            callback.accept(mPipTransitionState.isInPip());
        }
    }

    private void removeOnIsInPipStateChangedListener(@NonNull Consumer<Boolean> callback) {
        if (callback != null) {
            mOnIsInPipStateChangedListeners.remove(callback);
        }
    }

    private void setLauncherAppIconSize(int iconSizePx) {
        mPipBoundsState.getLauncherState().setAppIconSizePx(iconSizePx);
    }

    /**
     * The interface for calls from outside the Shell, within the host process.
     */
    public class PipImpl implements Pip {
        @Override
        public void expandPip() {}

        @Override
        public void onSystemUiStateChanged(boolean isSysUiStateValid, long flag) {}

        @Override
        public void addOnIsInPipStateChangedListener(@NonNull Consumer<Boolean> callback) {
            mMainExecutor.execute(() -> {
                PipController.this.addOnIsInPipStateChangedListener(callback);
            });
        }

        @Override
        public void removeOnIsInPipStateChangedListener(@NonNull Consumer<Boolean> callback) {
            mMainExecutor.execute(() -> {
                PipController.this.removeOnIsInPipStateChangedListener(callback);
            });
        }

        @Override
        public void addPipExclusionBoundsChangeListener(Consumer<Rect> listener) {
            mMainExecutor.execute(() -> {
                mPipBoundsState.addPipExclusionBoundsChangeCallback(listener);
            });
        }

        @Override
        public void removePipExclusionBoundsChangeListener(Consumer<Rect> listener) {
            mMainExecutor.execute(() -> {
                mPipBoundsState.removePipExclusionBoundsChangeCallback(listener);
            });
        }

        @Override
        public void showPictureInPictureMenu() {}
    }

    /**
     * The interface for calls from outside the host process.
     */
    @BinderThread
    private static class IPipImpl extends IPip.Stub implements ExternalInterfaceBinder {
        private PipController mController;
        private final SingleInstanceRemoteListener<PipController, IPipAnimationListener> mListener;
        private final PipAnimationListener mPipAnimationListener = new PipAnimationListener() {
            @Override
            public void onPipAnimationStarted() {
                mListener.call(l -> l.onPipAnimationStarted());
            }

            @Override
            public void onPipResourceDimensionsChanged(int cornerRadius, int shadowRadius) {
                mListener.call(l -> l.onPipResourceDimensionsChanged(cornerRadius, shadowRadius));
            }

            @Override
            public void onExpandPip() {
                mListener.call(l -> l.onExpandPip());
            }
        };

        IPipImpl(PipController controller) {
            mController = controller;
            mListener = new SingleInstanceRemoteListener<>(mController,
                    (cntrl) -> cntrl.setPipRecentsAnimationListener(mPipAnimationListener),
                    (cntrl) -> cntrl.setPipRecentsAnimationListener(null));
        }

        /**
         * Invalidates this instance, preventing future calls from updating the controller.
         */
        @Override
        public void invalidate() {
            mController = null;
            // Unregister the listener to ensure any registered binder death recipients are unlinked
            mListener.unregister();
        }

        @Override
        public Rect startSwipePipToHome(ActivityManager.RunningTaskInfo taskInfo,
                int launcherRotation, Rect keepClearArea) {
            Rect[] result = new Rect[1];
            executeRemoteCallWithTaskPermission(mController, "startSwipePipToHome",
                    (controller) -> {
                        result[0] = controller.getSwipePipToHomeBounds(taskInfo.topActivity,
                                taskInfo.topActivityInfo, taskInfo.displayId,
                                taskInfo.pictureInPictureParams, launcherRotation, keepClearArea);
                    }, true /* blocking */);
            return result[0];
        }

        @Override
        public void stopSwipePipToHome(int taskId, ComponentName componentName,
                Rect destinationBounds, SurfaceControl overlay, Rect appBounds,
                Rect sourceRectHint) {
            if (overlay != null) {
                overlay.setUnreleasedWarningCallSite("PipController.stopSwipePipToHome");
            }
            executeRemoteCallWithTaskPermission(mController, "stopSwipePipToHome",
                    (controller) -> controller.onSwipePipToHomeAnimationStart(
                            taskId, componentName, destinationBounds, overlay, appBounds,
                            sourceRectHint));
        }

        @Override
        public void abortSwipePipToHome(int taskId, ComponentName componentName) {}

        @Override
        public void setShelfHeight(boolean visible, int height) {}

        @Override
        public void setLauncherKeepClearAreaHeight(boolean visible, int height) {
            executeRemoteCallWithTaskPermission(mController, "setLauncherKeepClearAreaHeight",
                    (controller) -> controller.setLauncherKeepClearAreaHeight(visible, height));
        }

        @Override
        public void setLauncherAppIconSize(int iconSizePx) {
            executeRemoteCallWithTaskPermission(mController, "setLauncherAppIconSize",
                    (controller) -> controller.setLauncherAppIconSize(iconSizePx));
        }

        @Override
        public void setPipAnimationListener(IPipAnimationListener listener) {
            executeRemoteCallWithTaskPermission(mController, "setPipAnimationListener",
                    (controller) -> {
                        if (listener != null) {
                            mListener.register(listener);
                        } else {
                            mListener.unregister();
                        }
                    });
        }

        @Override
        public void setPipAnimationTypeToAlpha() {}
    }
}
