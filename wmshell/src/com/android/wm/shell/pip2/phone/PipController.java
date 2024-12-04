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

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;

import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_PIP;

import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.InsetsState;
import android.view.SurfaceControl;

import androidx.annotation.BinderThread;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.common.ProtoLog;
import com.android.internal.util.Preconditions;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SingleInstanceRemoteListener;
import com.android.wm.shell.common.TaskStackListenerCallback;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.common.pip.IPip;
import com.android.wm.shell.common.pip.IPipAnimationListener;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import java.io.PrintWriter;

/**
 * Manages the picture-in-picture (PIP) UI and states for Phones.
 */
public class PipController implements ConfigurationChangeListener,
        PipTransitionState.PipTransitionStateChangedListener,
        DisplayController.OnDisplaysChangedListener, RemoteCallable<PipController> {
    private static final String TAG = PipController.class.getSimpleName();
    private static final String SWIPE_TO_PIP_APP_BOUNDS = "pip_app_bounds";
    private static final String SWIPE_TO_PIP_OVERLAY = "swipe_to_pip_overlay";

    private final Context mContext;
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
    private final ShellExecutor mMainExecutor;

    // Wrapper for making Binder calls into PiP animation listener hosted in launcher's Recents.
    private PipAnimationListener mPipRecentsAnimationListener;

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
            ShellExecutor mainExecutor) {
        mContext = context;
        mShellCommandHandler = shellCommandHandler;
        mShellController = shellController;
        mDisplayController = displayController;
        mDisplayInsetsController = displayInsetsController;
        mPipBoundsState = pipBoundsState;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipScheduler = pipScheduler;
        mTaskStackListener = taskStackListener;
        mShellTaskOrganizer = shellTaskOrganizer;
        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this);
        mMainExecutor = mainExecutor;

        if (PipUtils.isPip2ExperimentEnabled()) {
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
            ShellExecutor mainExecutor) {
        if (!context.getPackageManager().hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Device doesn't support Pip feature", TAG);
            return null;
        }
        return new PipController(context, shellInit, shellCommandHandler, shellController,
                displayController, displayInsetsController, pipBoundsState, pipBoundsAlgorithm,
                pipDisplayLayoutState, pipScheduler, taskStackListener, shellTaskOrganizer,
                pipTransitionState, mainExecutor);
    }

    private void onInit() {
        mShellCommandHandler.addDumpCallback(this::dump, this);
        // Ensure that we have the display info in case we get calls to update the bounds before the
        // listener calls back
        mPipDisplayLayoutState.setDisplayId(mContext.getDisplayId());
        DisplayLayout layout = new DisplayLayout(mContext, mContext.getDisplay());
        mPipDisplayLayoutState.setDisplayLayout(layout);

        mDisplayController.addDisplayWindowListener(this);
        mDisplayInsetsController.addInsetsChangedListener(mPipDisplayLayoutState.getDisplayId(),
                new DisplayInsetsController.OnInsetsChangedListener() {
                    @Override
                    public void insetsChanged(InsetsState insetsState) {
                        onDisplayChanged(mDisplayController
                                        .getDisplayLayout(mPipDisplayLayoutState.getDisplayId()));
                    }
                });

        // Allow other outside processes to bind to PiP controller using the key below.
        mShellController.addExternalInterface(KEY_EXTRA_SHELL_PIP,
                this::createExternalInterface, this);
        mShellController.addConfigurationChangeListener(this);

        mTaskStackListener.addListener(new TaskStackListenerCallback() {
            @Override
            public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                    boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
                if (task.getWindowingMode() != WINDOWING_MODE_PINNED) {
                    return;
                }
                mPipScheduler.scheduleExitPipViaExpand();
            }
        });
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
    }

    @Override
    public void onDensityOrFontScaleChanged() {
        onPipResourceDimensionsChanged();
    }

    @Override
    public void onThemeChanged() {
        onDisplayChanged(new DisplayLayout(mContext, mContext.getDisplay()));
    }

    //
    // DisplayController.OnDisplaysChangedListener implementations
    //

    @Override
    public void onDisplayAdded(int displayId) {
        if (displayId != mPipDisplayLayoutState.getDisplayId()) {
            return;
        }
        onDisplayChanged(mDisplayController.getDisplayLayout(displayId));
    }

    @Override
    public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        if (displayId != mPipDisplayLayoutState.getDisplayId()) {
            return;
        }
        onDisplayChanged(mDisplayController.getDisplayLayout(displayId));
    }

    private void onDisplayChanged(DisplayLayout layout) {
        mPipDisplayLayoutState.setDisplayLayout(layout);
    }

    //
    // IPip Binder stub helpers
    //

    private Rect getSwipePipToHomeBounds(ComponentName componentName, ActivityInfo activityInfo,
            PictureInPictureParams pictureInPictureParams,
            int launcherRotation, Rect hotseatKeepClearArea) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "getSwipePipToHomeBounds: %s", componentName);
        mPipBoundsState.setBoundsStateForEntry(componentName, activityInfo, pictureInPictureParams,
                mPipBoundsAlgorithm);
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
        mPipRecentsAnimationListener.onPipAnimationStarted();
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        if (newState == PipTransitionState.SWIPING_TO_PIP) {
            Preconditions.checkState(extra != null,
                    "No extra bundle for " + mPipTransitionState);

            SurfaceControl overlay = extra.getParcelable(
                    SWIPE_TO_PIP_OVERLAY, SurfaceControl.class);
            Rect appBounds = extra.getParcelable(
                    SWIPE_TO_PIP_APP_BOUNDS, Rect.class);

            Preconditions.checkState(appBounds != null,
                    "App bounds can't be null for " + mPipTransitionState);
            mPipTransitionState.setSwipePipToHomeState(overlay, appBounds);
        } else if (newState == PipTransitionState.ENTERED_PIP) {
            if (mPipTransitionState.isInSwipePipToHomeTransition()) {
                mPipTransitionState.resetSwipePipToHomeState();
            }
        }
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

    private void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = "  ";
        pw.println(TAG);
        mPipBoundsAlgorithm.dump(pw, innerPrefix);
        mPipBoundsState.dump(pw, innerPrefix);
        mPipDisplayLayoutState.dump(pw, innerPrefix);
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
        public Rect startSwipePipToHome(ComponentName componentName, ActivityInfo activityInfo,
                PictureInPictureParams pictureInPictureParams, int launcherRotation,
                Rect keepClearArea) {
            Rect[] result = new Rect[1];
            executeRemoteCallWithTaskPermission(mController, "startSwipePipToHome",
                    (controller) -> {
                        result[0] = controller.getSwipePipToHomeBounds(componentName, activityInfo,
                                pictureInPictureParams, launcherRotation, keepClearArea);
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
        public void setLauncherKeepClearAreaHeight(boolean visible, int height) {}

        @Override
        public void setLauncherAppIconSize(int iconSizePx) {}

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
