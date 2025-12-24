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

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.DragEvent.ACTION_DRAG_ENDED;
import static android.view.DragEvent.ACTION_DRAG_ENTERED;
import static android.view.DragEvent.ACTION_DRAG_EXITED;
import static android.view.DragEvent.ACTION_DRAG_LOCATION;
import static android.view.DragEvent.ACTION_DRAG_STARTED;
import static android.view.DragEvent.ACTION_DROP;
import static android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.PendingIntent;
import android.content.ClipDescription;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.HardwareRenderer;
import android.graphics.PixelFormat;
import android.util.Slog;
import android.util.SparseArray;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.logging.UiEventLogger;
import com.android.internal.protolog.ProtoLog;
import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.bubbles.bar.DragToBubbleController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.annotations.ExternalMainThread;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import dagger.Lazy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Handles the global drag and drop handling for the Shell.
 */
public class DragAndDropController implements RemoteCallable<DragAndDropController>,
        GlobalDragListener.GlobalDragListenerCallback,
        DisplayController.OnDisplaysChangedListener,
        ShellTaskOrganizer.TaskVanishedListener,
        View.OnDragListener, ComponentCallbacks2 {

    private static final String TAG = DragAndDropController.class.getSimpleName();

    private final Context mContext;
    private final ShellController mShellController;
    private final ShellCommandHandler mShellCommandHandler;
    private final ShellTaskOrganizer mShellTaskOrganizer;
    private final DisplayController mDisplayController;
    private final DragAndDropEventLogger mLogger;
    private final IconProvider mIconProvider;
    private final GlobalDragListener mGlobalDragListener;
    private final Transitions mTransitions;
    private final DesktopState mDesktopState;
    private SplitScreenController mSplitScreen;
    private Lazy<DragToBubbleController> mDragToBubbleController;
    private ShellExecutor mMainExecutor;
    private ArrayList<DragAndDropListener> mListeners = new ArrayList<>();

    // Map of displayId -> per-display info
    private final SparseArray<PerDisplay> mDisplayDropTargets = new SparseArray<>();

    // The current display if a drag is in progress
    private int mActiveDragDisplay = -1;

    /**
     * Listener called during drag events.
     */
    public interface DragAndDropListener {
        /** Called when a drag has started. */
        default void onDragStarted() {}

        /** Called when a drag has ended. */
        default void onDragEnded() {}

        /**
         * Called when an unhandled drag has occurred. The impl must return true if it decides to
         * handled the unhandled drag, and it must also call `onFinishCallback` to complete the
         * drag.
         */
        default boolean onUnhandledDrag(@NonNull PendingIntent launchIntent,
                @UserIdInt int userId,
                @NonNull DragEvent dragEvent,
                @NonNull Consumer<Boolean> onFinishCallback) {
            return false;
        }

        /** Called when the configuration changed. */
        default void onConfigurationChanged() {}
    }

    public DragAndDropController(Context context,
            ShellInit shellInit,
            ShellController shellController,
            ShellCommandHandler shellCommandHandler,
            ShellTaskOrganizer shellTaskOrganizer,
            DisplayController displayController,
            UiEventLogger uiEventLogger,
            IconProvider iconProvider,
            GlobalDragListener globalDragListener,
            Transitions transitions,
            Lazy<DragToBubbleController> dragToBubbleControllerLazy,
            ShellExecutor mainExecutor,
            DesktopState desktopState) {
        mContext = context;
        mShellController = shellController;
        mShellCommandHandler = shellCommandHandler;
        mShellTaskOrganizer = shellTaskOrganizer;
        mDisplayController = displayController;
        mLogger = new DragAndDropEventLogger(uiEventLogger);
        mIconProvider = iconProvider;
        mGlobalDragListener = globalDragListener;
        mTransitions = transitions;
        mDragToBubbleController = dragToBubbleControllerLazy;
        mMainExecutor = mainExecutor;
        mDesktopState = desktopState;
        shellInit.addInitCallback(this::onInit, this);
    }

    /**
     * Called when the controller is initialized.
     */
    public void onInit() {
        // TODO(b/238217847): The dependency from SplitscreenController on DragAndDropController is
        // inverted, which leads to SplitscreenController not setting its instance until after
        // onDisplayAdded.  We can remove this post once we fix that dependency.
        mMainExecutor.executeDelayed(() -> {
            mDisplayController.addDisplayWindowListener(this);
        }, 0);
        mShellController.addExternalInterface(IDragAndDrop.DESCRIPTOR,
                this::createExternalInterface, this);
        mShellTaskOrganizer.addTaskVanishedListener(this);
        mShellCommandHandler.addDumpCallback(this::dump, this);
        mGlobalDragListener.setListener(this);
        addListener(mDragToBubbleController.get());
    }

    private ExternalInterfaceBinder createExternalInterface() {
        return new IDragAndDropImpl(this);
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mMainExecutor;
    }

    /**
     * Sets the splitscreen controller to use if the feature is available.
     */
    public void setSplitScreenController(SplitScreenController splitscreen) {
        mSplitScreen = splitscreen;
    }

    /** Adds a listener to be notified of drag and drop events. */
    public void addListener(DragAndDropListener listener) {
        mListeners.add(listener);
    }

    /** Removes a drag and drop listener. */
    public void removeListener(DragAndDropListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Notifies all listeners and returns whether any listener handled the callback.
     */
    private boolean notifyListeners(Function<DragAndDropListener, Boolean> callback) {
        for (int i = 0; i < mListeners.size(); i++) {
            boolean handled = callback.apply(mListeners.get(i));
            if (handled) {
                // Return once the callback reports it has handled it
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDisplayAdded(int displayId) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "Display added: %d", displayId);
        if (displayId != DEFAULT_DISPLAY) {
            // Ignore non-default displays for now
            return;
        }

        final Context context = mDisplayController.getDisplayContext(displayId)
                .createWindowContext(TYPE_APPLICATION_OVERLAY, null);
        final WindowManager wm = context.getSystemService(WindowManager.class);
        final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                TYPE_APPLICATION_OVERLAY,
                FLAG_NOT_FOCUSABLE | FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        layoutParams.privateFlags |= SYSTEM_FLAG_SHOW_FOR_ALL_USERS
                | PRIVATE_FLAG_INTERCEPT_GLOBAL_DRAG_AND_DROP
                | PRIVATE_FLAG_NO_MOVE_ANIMATION;
        layoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        layoutParams.setFitInsetsTypes(0);
        layoutParams.setTitle("ShellDropTarget");
        FrameLayout rootView = (FrameLayout) LayoutInflater.from(context).inflate(
                R.layout.global_drop_target, null);
        rootView.setOnDragListener(this);
        rootView.setVisibility(View.INVISIBLE);
        DragToBubbleController dragToBubbleController = null;
        boolean isPrimaryDisplay = context.getDisplayId() == displayId;
        // only add bubble bar drop targets on primary display
        if (isPrimaryDisplay) {
            dragToBubbleController = mDragToBubbleController.get();
            rootView.addView(dragToBubbleController.getDropTargetContainer());
        }
        DragLayoutProvider dragLayout = new DragLayout(context, mSplitScreen,
                dragToBubbleController, mIconProvider);
        dragLayout.addDraggingView(rootView);
        try {
            wm.addView(rootView, layoutParams);
            addDisplayDropTarget(displayId, context, wm, rootView, dragLayout);
            context.registerComponentCallbacks(this);
        } catch (WindowManager.InvalidDisplayException e) {
            Slog.w(TAG, "Unable to add view for display id: " + displayId);
        }
    }

    @VisibleForTesting
    void addDisplayDropTarget(int displayId, Context context, WindowManager wm,
            FrameLayout rootView, DragLayoutProvider dragLayout) {
        mDisplayDropTargets.put(displayId,
                new PerDisplay(displayId, context, wm, rootView, dragLayout));
    }

    @Override
    public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "Display changed: %d", displayId);
        final PerDisplay pd = mDisplayDropTargets.get(displayId);
        if (pd == null) {
            return;
        }
        pd.rootView.requestApplyInsets();
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP, "Display removed: %d", displayId);
        final PerDisplay pd = mDisplayDropTargets.get(displayId);
        if (pd == null) {
            return;
        }
        pd.context.unregisterComponentCallbacks(this);
        pd.wm.removeViewImmediate(pd.rootView);
        mDisplayDropTargets.remove(displayId);
    }

    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo.baseIntent == null) {
            // Invalid info
            return;
        }
        // Find the active drag
        PerDisplay pd = null;
        for (int i = 0; i < mDisplayDropTargets.size(); i++) {
            final PerDisplay iPd = mDisplayDropTargets.valueAt(i);
            if (iPd.isHandlingDrag) {
                pd = iPd;
                break;
            }
        }
        if (pd == null || pd.activeDragCount <= 0 || !pd.isHandlingDrag) {
            // Not currently dragging
            return;
        }

        // Update the drag session
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                "Handling vanished task: id=%d component=%s", taskInfo.taskId,
                taskInfo.baseIntent.getComponent());
        pd.dragSession.updateRunningTask();
        pd.dragLayout.updateSession(pd.dragSession);
    }

    @Override
    public boolean onDrag(View target, DragEvent event) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                "Drag event: action=%s x=%f y=%f xOffset=%f yOffset=%f",
                DragEvent.actionToString(event.getAction()), event.getX(), event.getY(),
                event.getOffsetX(), event.getOffsetY());
        final int displayId = target.getDisplay().getDisplayId();
        final PerDisplay pd = mDisplayDropTargets.get(displayId);
        final ClipDescription description = event.getClipDescription();

        if (pd == null) {
            return false;
        }

        DragSession dragSession = null;
        if (event.getAction() == ACTION_DRAG_STARTED) {
            mActiveDragDisplay = displayId;
            dragSession = new DragSession(ActivityTaskManager.getInstance(),
                    mDisplayController.getDisplayLayout(displayId), event.getClipData(),
                    event.getDragFlags());
            // Only update the running task for now to determine if we should defer to desktop to
            // handle the drag
            dragSession.updateRunningTask();
            final ActivityManager.RunningTaskInfo taskInfo = dragSession.runningTaskInfo;
            // Desktop tasks will have their own drag handling.
            final boolean isDesktopDrag = taskInfo != null && taskInfo.isFreeform()
                    && mDesktopState.canEnterDesktopMode();
            pd.isHandlingDrag = DragUtils.canHandleDrag(event) && !isDesktopDrag;
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                    "Clip description: handlingDrag=%b itemCount=%d mimeTypes=%s flags=%s",
                    pd.isHandlingDrag,
                    event.getClipData() != null ? event.getClipData().getItemCount() : -1,
                    DragUtils.getMimeTypesConcatenated(description),
                    DragUtils.dragFlagsToString(event.getDragFlags()));
        }

        if (!pd.isHandlingDrag) {
            return false;
        }

        switch (event.getAction()) {
            case ACTION_DRAG_STARTED:
                if (pd.activeDragCount != 0) {
                    Slog.w(TAG, "Unexpected drag start during an active drag");
                    return false;
                }
                // Only initialize the session after we've checked that we're handling the drag
                dragSession.initialize(true /* skipUpdateRunningTask */);
                pd.dragSession = dragSession;
                pd.activeDragCount++;
                pd.dragLayout.prepare(pd.dragSession, mLogger.logStart(pd.dragSession));
                if (pd.dragSession.hideDragSourceTaskId != -1) {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                            "Hiding task surface: taskId=%d", pd.dragSession.hideDragSourceTaskId);
                    mShellTaskOrganizer.setTaskSurfaceVisibility(
                            pd.dragSession.hideDragSourceTaskId, false /* visible */);
                }
                setDropTargetWindowVisibility(pd, View.VISIBLE);
                notifyListeners(l -> {
                    l.onDragStarted();
                    // Return false to continue dispatch to next listener
                    return false;
                });
                break;
            case ACTION_DRAG_ENTERED:
                pd.dragLayout.show();
                break;
            case ACTION_DRAG_LOCATION:
                pd.dragLayout.update(event);
                break;
            case ACTION_DROP: {
                pd.dragLayout.update(event);
                return handleDrop(event, pd);
            }
            case ACTION_DRAG_EXITED: {
                // Either one of DROP or EXITED will happen, and when EXITED we won't consume
                // the drag surface
                pd.dragLayout.hide(event, null);
                break;
            }
            case ACTION_DRAG_ENDED:
                // TODO(b/290391688): Ensure sure it's not possible to get ENDED without DROP
                // or EXITED
                if (pd.dragLayout.hasDropped()) {
                    mLogger.logDrop();
                } else {
                    if (pd.dragSession.hideDragSourceTaskId != -1) {
                        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                                "Re-showing task surface: taskId=%d",
                                pd.dragSession.hideDragSourceTaskId);
                        mShellTaskOrganizer.setTaskSurfaceVisibility(
                                pd.dragSession.hideDragSourceTaskId, true /* visible */);
                    }
                    pd.activeDragCount--;
                    pd.dragLayout.hide(event, () -> {
                        if (pd.activeDragCount == 0) {
                            // Hide the window if another drag hasn't been started while animating
                            // the drag-end
                            setDropTargetWindowVisibility(pd, View.INVISIBLE);
                        }
                    });
                }
                mLogger.logEnd();
                mActiveDragDisplay = -1;
                notifyListeners(l -> {
                    l.onDragEnded();
                    // Return false to continue dispatch to next listener
                    return false;
                });
                break;
        }
        return true;
    }

    @Override
    public void onCrossWindowDrop(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        // Bring the task forward when an item is dropped on it
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.reorder(taskInfo.token, true /* onTop */);
        mTransitions.startTransition(WindowManager.TRANSIT_TO_FRONT, wct, null);
    }

    @Override
    public void onUnhandledDrop(@NonNull DragEvent dragEvent,
            @NonNull Consumer<Boolean> onFinishCallback) {
        final PendingIntent launchIntent = DragUtils.getLaunchIntent(dragEvent);
        if (launchIntent == null) {
            // No intent to launch, report that this is unhandled by the listener
            onFinishCallback.accept(false);
            return;
        }

        // TODO(b/391624027): Consider piping through launch intent user if needed later
        final int userId = launchIntent.getCreatorUserHandle().getIdentifier();
        final boolean handled = notifyListeners(
                l -> l.onUnhandledDrag(launchIntent, userId, dragEvent, onFinishCallback));
        if (!handled) {
            // Nobody handled this, we still have to notify WM
            onFinishCallback.accept(false);
        }
    }

    /**
     * Handles dropping on the drop target.
     */
    private boolean handleDrop(DragEvent event, PerDisplay pd) {
        final SurfaceControl dragSurface = event.getDragSurface();
        pd.activeDragCount--;
        // Find the token of the task to hide as a part of entering split
        WindowContainerToken hideTaskToken = null;
        if (pd.dragSession.hideDragSourceTaskId != -1) {
            ActivityManager.RunningTaskInfo info = mShellTaskOrganizer.getRunningTaskInfo(
                    pd.dragSession.hideDragSourceTaskId);
            if (info != null) {
                hideTaskToken = info.token;
            }
        }
        return pd.dragLayout.drop(event, dragSurface, hideTaskToken, () -> {
            if (pd.activeDragCount == 0) {
                // Hide the window if another drag hasn't been started while animating the drop
                setDropTargetWindowVisibility(pd, View.INVISIBLE);
            }
        });
    }

    private void setDropTargetWindowVisibility(PerDisplay pd, int visibility) {
        pd.setWindowVisibility(visibility);
    }

    /**
     * Returns if any displays are currently ready to handle a drag/drop.
     */
    private boolean isReadyToHandleDrag() {
        for (int i = 0; i < mDisplayDropTargets.size(); i++) {
            if (mDisplayDropTargets.valueAt(i).hasDrawn) {
                return true;
            }
        }
        return false;
    }

    // Note: Component callbacks are always called on the main thread of the process
    @ExternalMainThread
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mMainExecutor.execute(() -> {
            for (int i = 0; i < mDisplayDropTargets.size(); i++) {
                mDisplayDropTargets.get(i).dragLayout.onConfigChanged(newConfig);
            }
            for (DragAndDropListener listener : mListeners) {
                listener.onConfigurationChanged();
            }
        });
    }

    // Note: Component callbacks are always called on the main thread of the process
    @ExternalMainThread
    @Override
    public void onTrimMemory(int level) {
        // Do nothing
    }

    // Note: Component callbacks are always called on the main thread of the process
    @ExternalMainThread
    @Override
    public void onLowMemory() {
        // Do nothing
    }

    /**
     * Dumps information about this controller.
     */
    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "listeners=" + mListeners.size());
        pw.println(innerPrefix + "Per display:");
        for (int i = 0; i < mDisplayDropTargets.size(); i++) {
            mDisplayDropTargets.valueAt(i).dump(pw, innerPrefix);
        }
    }
    
    /**
     * The interface for calls from outside the host process.
     */
    @BinderThread
    private static class IDragAndDropImpl extends IDragAndDrop.Stub
            implements ExternalInterfaceBinder {
        private DragAndDropController mController;

        public IDragAndDropImpl(DragAndDropController controller) {
            mController = controller;
        }

        /**
         * Invalidates this instance, preventing future calls from updating the controller.
         */
        @Override
        public void invalidate() {
            mController = null;
        }

        @Override
        public boolean isReadyToHandleDrag() {
            boolean[] result = new boolean[1];
            executeRemoteCallWithTaskPermission(mController, "isReadyToHandleDrag",
                    controller -> result[0] = controller.isReadyToHandleDrag(),
                    true /* blocking */
            );
            return result[0];
        }
    }

    private static class PerDisplay implements HardwareRenderer.FrameDrawingCallback {
        final int displayId;
        final Context context;
        final WindowManager wm;
        final FrameLayout rootView;
        final DragLayoutProvider dragLayout;
        // Tracks whether the window has fully drawn since it was last made visible
        boolean hasDrawn;

        boolean isHandlingDrag;
        // A count of the number of active drags in progress to ensure that we only hide the window
        // when all the drag animations have completed
        int activeDragCount;
        // The active drag session
        DragSession dragSession;

        PerDisplay(int dispId, Context c, WindowManager w, FrameLayout rv, DragLayoutProvider dl) {
            displayId = dispId;
            context = c;
            wm = w;
            rootView = rv;
            dragLayout = dl;
        }

        private void setWindowVisibility(int visibility) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_DRAG_AND_DROP,
                    "Set drop target window visibility: displayId=%d visibility=%d",
                    displayId, visibility);
            rootView.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                rootView.requestApplyInsets();
                if (!hasDrawn && rootView.getViewRootImpl() != null) {
                    rootView.getViewRootImpl().registerRtFrameCallback(this);
                }
            } else {
                hasDrawn = false;
            }
        }

        @Override
        public void onFrameDraw(long frame) {
            hasDrawn = true;
        }

        /**
         * Dumps information about this display's shell drop target.
         */
        public void dump(@NonNull PrintWriter pw, String prefix) {
            final String innerPrefix = prefix + "  ";
            pw.println(innerPrefix + "displayId=" + displayId);
            pw.println(innerPrefix + "hasDrawn=" + hasDrawn);
            pw.println(innerPrefix + "isHandlingDrag=" + isHandlingDrag);
            pw.println(innerPrefix + "activeDragCount=" + activeDragCount);
            dragLayout.dump(pw, innerPrefix);
        }
    }
}
