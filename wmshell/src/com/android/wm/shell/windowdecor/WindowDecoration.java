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

package com.android.wm.shell.windowdecor;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.res.Configuration.DENSITY_DPI_UNDEFINED;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowInsets.Type.mandatorySystemGestures;
import static android.view.WindowInsets.Type.statusBars;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.WindowConfiguration.WindowingMode;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Binder;
import android.os.Trace;
import android.view.Display;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.LayoutInflater;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowlessWindowManager;
import android.window.SurfaceSyncGroup;
import android.window.TaskConstants;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.shared.DesktopModeStatus;
import com.android.wm.shell.windowdecor.WindowDecoration.RelayoutParams.OccludingCaptionElement;
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalViewHostViewContainer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Manages a container surface and a windowless window to show window decoration. Responsible to
 * update window decoration window state and layout parameters on task info changes and so that
 * window decoration is in correct state and bounds.
 *
 * The container surface is a child of the task display area in the same display, so that window
 * decorations can be drawn out of the task bounds and receive input events from out of the task
 * bounds to support drag resizing.
 *
 * The windowless window that hosts window decoration is positioned in front of all activities, to
 * allow the foreground activity to draw its own background behind window decorations, such as
 * the window captions.
 *
 * @param <T> The type of the root view
 */
public abstract class WindowDecoration<T extends View & TaskFocusStateConsumer>
        implements AutoCloseable {

    /**
     * The Z-order of {@link #mCaptionContainerSurface}.
     * <p>
     * We use {@link #mDecorationContainerSurface} to define input window for task resizing; by
     * layering it in front of {@link #mCaptionContainerSurface}, we can allow it to handle input
     * prior to caption view itself, treating corner inputs as resize events rather than
     * repositioning.
     */
    static final int CAPTION_LAYER_Z_ORDER = -1;
    /**
     * The Z-order of the task input sink in {@link DragPositioningCallback}.
     * <p>
     * This task input sink is used to prevent undesired dispatching of motion events out of task
     * bounds; by layering it behind {@link #mCaptionContainerSurface}, we allow captions to handle
     * input events first.
     */
    static final int INPUT_SINK_Z_ORDER = -2;

    /**
     * System-wide context. Only used to create context with overridden configurations.
     */
    final Context mContext;
    final DisplayController mDisplayController;
    final ShellTaskOrganizer mTaskOrganizer;
    final Supplier<SurfaceControl.Builder> mSurfaceControlBuilderSupplier;
    final Supplier<SurfaceControl.Transaction> mSurfaceControlTransactionSupplier;
    final Supplier<WindowContainerTransaction> mWindowContainerTransactionSupplier;
    final SurfaceControlViewHostFactory mSurfaceControlViewHostFactory;
    private final DisplayController.OnDisplaysChangedListener mOnDisplaysChangedListener =
            new DisplayController.OnDisplaysChangedListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    if (mTaskInfo.displayId != displayId) {
                        return;
                    }

                    mDisplayController.removeDisplayWindowListener(this);
                    relayout(mTaskInfo);
                }
            };

    RunningTaskInfo mTaskInfo;
    int mLayoutResId;
    final SurfaceControl mTaskSurface;

    Display mDisplay;
    Context mDecorWindowContext;
    SurfaceControl mDecorationContainerSurface;

    SurfaceControl mCaptionContainerSurface;
    private WindowlessWindowManager mCaptionWindowManager;
    private SurfaceControlViewHost mViewHost;
    private Configuration mWindowDecorConfig;
    TaskDragResizer mTaskDragResizer;
    private boolean mIsCaptionVisible;

    /** The most recent set of insets applied to this window decoration. */
    private WindowDecorationInsets mWindowDecorationInsets;
    private final Binder mOwner = new Binder();
    private final float[] mTmpColor = new float[3];

    WindowDecoration(
            Context context,
            DisplayController displayController,
            ShellTaskOrganizer taskOrganizer,
            RunningTaskInfo taskInfo,
            SurfaceControl taskSurface) {
        this(context, displayController, taskOrganizer, taskInfo, taskSurface,
                SurfaceControl.Builder::new, SurfaceControl.Transaction::new,
                WindowContainerTransaction::new, SurfaceControl::new,
                new SurfaceControlViewHostFactory() {});
    }

    WindowDecoration(
            Context context,
            DisplayController displayController,
            ShellTaskOrganizer taskOrganizer,
            RunningTaskInfo taskInfo,
            @NonNull SurfaceControl taskSurface,
            Supplier<SurfaceControl.Builder> surfaceControlBuilderSupplier,
            Supplier<SurfaceControl.Transaction> surfaceControlTransactionSupplier,
            Supplier<WindowContainerTransaction> windowContainerTransactionSupplier,
            Supplier<SurfaceControl> surfaceControlSupplier,
            SurfaceControlViewHostFactory surfaceControlViewHostFactory) {
        mContext = context;
        mDisplayController = displayController;
        mTaskOrganizer = taskOrganizer;
        mTaskInfo = taskInfo;
        mTaskSurface = cloneSurfaceControl(taskSurface, surfaceControlSupplier);
        mSurfaceControlBuilderSupplier = surfaceControlBuilderSupplier;
        mSurfaceControlTransactionSupplier = surfaceControlTransactionSupplier;
        mWindowContainerTransactionSupplier = windowContainerTransactionSupplier;
        mSurfaceControlViewHostFactory = surfaceControlViewHostFactory;

        mDisplay = mDisplayController.getDisplay(mTaskInfo.displayId);
    }

    /**
     * Used by {@link WindowDecoration} to trigger a new relayout because the requirements for a
     * relayout weren't satisfied are satisfied now.
     *
     * @param taskInfo The previous {@link RunningTaskInfo} passed into {@link #relayout} or the
     *                 constructor.
     */
    abstract void relayout(RunningTaskInfo taskInfo);

    /**
     * Used by the {@link DragPositioningCallback} associated with the implementing class to
     * enforce drags ending in a valid position. A null result means no restriction.
     */
    @Nullable
    abstract Rect calculateValidDragArea();

    void relayout(RelayoutParams params, SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT, WindowContainerTransaction wct, T rootView,
            RelayoutResult<T> outResult) {
        updateViewsAndSurfaces(params, startT, finishT, wct, rootView, outResult);
        if (outResult.mRootView != null) {
            updateViewHost(params, startT, outResult);
        }
    }

    protected void updateViewsAndSurfaces(RelayoutParams params,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            WindowContainerTransaction wct, T rootView, RelayoutResult<T> outResult) {
        outResult.reset();
        if (params.mRunningTaskInfo != null) {
            mTaskInfo = params.mRunningTaskInfo;
        }
        final int oldLayoutResId = mLayoutResId;
        mLayoutResId = params.mLayoutResId;

        if (!mTaskInfo.isVisible) {
            releaseViews(wct);
            finishT.hide(mTaskSurface);
            return;
        }

        inflateIfNeeded(params, wct, rootView, oldLayoutResId, outResult);
        if (outResult.mRootView == null) {
            // Didn't manage to create a root view, early out.
            return;
        }
        rootView = null; // Clear it just in case we use it accidentally

        updateCaptionVisibility(outResult.mRootView, mTaskInfo.displayId);

        final Rect taskBounds = mTaskInfo.getConfiguration().windowConfiguration.getBounds();
        outResult.mWidth = taskBounds.width();
        outResult.mHeight = taskBounds.height();
        outResult.mRootView.setTaskFocusState(mTaskInfo.isFocused);
        final Resources resources = mDecorWindowContext.getResources();
        outResult.mCaptionHeight = loadDimensionPixelSize(resources, params.mCaptionHeightId);
        outResult.mCaptionWidth = params.mCaptionWidthId != Resources.ID_NULL
                ? loadDimensionPixelSize(resources, params.mCaptionWidthId) : taskBounds.width();
        outResult.mCaptionX = (outResult.mWidth - outResult.mCaptionWidth) / 2;

        updateDecorationContainerSurface(startT, outResult);
        updateCaptionContainerSurface(startT, outResult);
        updateCaptionInsets(params, wct, outResult, taskBounds);
        updateTaskSurface(params, startT, finishT, outResult);
    }

    private void inflateIfNeeded(RelayoutParams params, WindowContainerTransaction wct,
            T rootView, int oldLayoutResId, RelayoutResult<T> outResult) {
        if (rootView == null && params.mLayoutResId == 0) {
            throw new IllegalArgumentException("layoutResId and rootView can't both be invalid.");
        }

        outResult.mRootView = rootView;
        final int oldDensityDpi = mWindowDecorConfig != null
                ? mWindowDecorConfig.densityDpi : DENSITY_DPI_UNDEFINED;
        final int oldNightMode =  mWindowDecorConfig != null
                ? (mWindowDecorConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                : Configuration.UI_MODE_NIGHT_UNDEFINED;
        mWindowDecorConfig = params.mWindowDecorConfig != null ? params.mWindowDecorConfig
                : mTaskInfo.getConfiguration();
        final int newDensityDpi = mWindowDecorConfig.densityDpi;
        final int newNightMode =  mWindowDecorConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        if (oldDensityDpi != newDensityDpi
                || mDisplay == null
                || mDisplay.getDisplayId() != mTaskInfo.displayId
                || oldLayoutResId != mLayoutResId
                || oldNightMode != newNightMode
                || mDecorWindowContext == null) {
            releaseViews(wct);

            if (!obtainDisplayOrRegisterListener()) {
                outResult.mRootView = null;
                return;
            }
            mDecorWindowContext = mContext.createConfigurationContext(mWindowDecorConfig);
            mDecorWindowContext.setTheme(mContext.getThemeResId());
            if (params.mLayoutResId != 0) {
                outResult.mRootView = (T) LayoutInflater.from(mDecorWindowContext)
                        .inflate(params.mLayoutResId, null);
            }
        }

        if (outResult.mRootView == null) {
            outResult.mRootView = (T) LayoutInflater.from(mDecorWindowContext)
                    .inflate(params.mLayoutResId, null);
        }
    }

    private void updateDecorationContainerSurface(
            SurfaceControl.Transaction startT, RelayoutResult<T> outResult) {
        if (mDecorationContainerSurface == null) {
            final SurfaceControl.Builder builder = mSurfaceControlBuilderSupplier.get();
            mDecorationContainerSurface = builder
                    .setName("Decor container of Task=" + mTaskInfo.taskId)
                    .setContainerLayer()
                    .setParent(mTaskSurface)
                    .setCallsite("WindowDecoration.updateDecorationContainerSurface")
                    .build();

            startT.setTrustedOverlay(mDecorationContainerSurface, true)
                    .setLayer(mDecorationContainerSurface,
                            TaskConstants.TASK_CHILD_LAYER_WINDOW_DECORATIONS);
        }

        startT.setWindowCrop(mDecorationContainerSurface, outResult.mWidth, outResult.mHeight)
                .show(mDecorationContainerSurface);
    }

    private void updateCaptionContainerSurface(
            SurfaceControl.Transaction startT, RelayoutResult<T> outResult) {
        if (mCaptionContainerSurface == null) {
            final SurfaceControl.Builder builder = mSurfaceControlBuilderSupplier.get();
            mCaptionContainerSurface = builder
                    .setName("Caption container of Task=" + mTaskInfo.taskId)
                    .setContainerLayer()
                    .setParent(mDecorationContainerSurface)
                    .setCallsite("WindowDecoration.updateCaptionContainerSurface")
                    .build();
        }

        startT.setWindowCrop(mCaptionContainerSurface, outResult.mCaptionWidth,
                        outResult.mCaptionHeight)
                .setPosition(mCaptionContainerSurface, outResult.mCaptionX, 0 /* y */)
                .setLayer(mCaptionContainerSurface, CAPTION_LAYER_Z_ORDER)
                .show(mCaptionContainerSurface);
    }

    private void updateCaptionInsets(RelayoutParams params, WindowContainerTransaction wct,
            RelayoutResult<T> outResult, Rect taskBounds) {
        if (!mIsCaptionVisible) {
            if (mWindowDecorationInsets != null) {
                mWindowDecorationInsets.remove(wct);
                mWindowDecorationInsets = null;
            }
            return;
        }
        // Caption inset is the full width of the task with the |captionHeight| and
        // positioned at the top of the task bounds, also in absolute coordinates.
        // So just reuse the task bounds and adjust the bottom coordinate.
        final Rect captionInsetsRect = new Rect(taskBounds);
        captionInsetsRect.bottom = captionInsetsRect.top + outResult.mCaptionHeight;

        // Caption bounding rectangles: these are optional, and are used to present finer
        // insets than traditional |Insets| to apps about where their content is occluded.
        // These are also in absolute coordinates.
        final Rect[] boundingRects;
        final int numOfElements = params.mOccludingCaptionElements.size();
        if (numOfElements == 0) {
            boundingRects = null;
        } else {
            // The customizable region can at most be equal to the caption bar.
            if (params.hasInputFeatureSpy()) {
                outResult.mCustomizableCaptionRegion.set(captionInsetsRect);
            }
            final Resources resources = mDecorWindowContext.getResources();
            boundingRects = new Rect[numOfElements];
            for (int i = 0; i < numOfElements; i++) {
                final OccludingCaptionElement element =
                        params.mOccludingCaptionElements.get(i);
                final int elementWidthPx =
                        resources.getDimensionPixelSize(element.mWidthResId);
                boundingRects[i] =
                        calculateBoundingRect(element, elementWidthPx, captionInsetsRect);
                // Subtract the regions used by the caption elements, the rest is
                // customizable.
                if (params.hasInputFeatureSpy()) {
                    outResult.mCustomizableCaptionRegion.op(boundingRects[i],
                            Region.Op.DIFFERENCE);
                }
            }
        }

        final WindowDecorationInsets newInsets = new WindowDecorationInsets(
                mTaskInfo.token, mOwner, captionInsetsRect, boundingRects);
        if (!newInsets.equals(mWindowDecorationInsets)) {
            // Add or update this caption as an insets source.
            mWindowDecorationInsets = newInsets;
            mWindowDecorationInsets.addOrUpdate(wct);
        }
    }

    private void updateTaskSurface(RelayoutParams params, SurfaceControl.Transaction startT,
            SurfaceControl.Transaction finishT, RelayoutResult<T> outResult) {
        if (params.mSetTaskPositionAndCrop) {
            final Point taskPosition = mTaskInfo.positionInParent;
            startT.setWindowCrop(mTaskSurface, outResult.mWidth, outResult.mHeight);
            finishT.setWindowCrop(mTaskSurface, outResult.mWidth, outResult.mHeight)
                    .setPosition(mTaskSurface, taskPosition.x, taskPosition.y);
        }

        float shadowRadius;
        if (mTaskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
            // Shadow is not needed for fullscreen tasks
            shadowRadius = 0;
        } else {
            shadowRadius =
                    loadDimension(mDecorWindowContext.getResources(), params.mShadowRadiusId);
        }
        startT.setShadowRadius(mTaskSurface, shadowRadius).show(mTaskSurface);
        finishT.setShadowRadius(mTaskSurface, shadowRadius);

        if (mTaskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM) {
            if (!DesktopModeStatus.isVeiledResizeEnabled()) {
                // When fluid resize is enabled, add a background to freeform tasks
                int backgroundColorInt = mTaskInfo.taskDescription.getBackgroundColor();
                mTmpColor[0] = (float) Color.red(backgroundColorInt) / 255.f;
                mTmpColor[1] = (float) Color.green(backgroundColorInt) / 255.f;
                mTmpColor[2] = (float) Color.blue(backgroundColorInt) / 255.f;
                startT.setColor(mTaskSurface, mTmpColor);
            }
            startT.setCornerRadius(mTaskSurface, params.mCornerRadius);
            finishT.setCornerRadius(mTaskSurface, params.mCornerRadius);
        } else if (!DesktopModeStatus.isVeiledResizeEnabled()) {
            startT.unsetColor(mTaskSurface);
        }
    }

    /**
     * Updates a {@link SurfaceControlViewHost} to connect the window decoration surfaces with our
     * View hierarchy.
     *
     * @param params parameters to use from the last relayout
     * @param onDrawTransaction a transaction to apply in sync with #onDraw
     * @param outResult results to use from the last relayout
     *
     */
    protected void updateViewHost(RelayoutParams params,
            SurfaceControl.Transaction onDrawTransaction, RelayoutResult<T> outResult) {
        Trace.beginSection("CaptionViewHostLayout");
        if (mCaptionWindowManager == null) {
            // Put caption under a container surface because ViewRootImpl sets the destination frame
            // of windowless window layers and BLASTBufferQueue#update() doesn't support offset.
            mCaptionWindowManager = new WindowlessWindowManager(
                    mTaskInfo.getConfiguration(), mCaptionContainerSurface,
                    null /* hostInputToken */);
        }
        mCaptionWindowManager.setConfiguration(mTaskInfo.getConfiguration());
        final WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(outResult.mCaptionWidth, outResult.mCaptionHeight,
                        TYPE_APPLICATION,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSPARENT);
        lp.setTitle("Caption of Task=" + mTaskInfo.taskId);
        lp.setTrustedOverlay();
        lp.inputFeatures = params.mInputFeatures;
        if (mViewHost == null) {
            Trace.beginSection("CaptionViewHostLayout-new");
            mViewHost = mSurfaceControlViewHostFactory.create(mDecorWindowContext, mDisplay,
                    mCaptionWindowManager);
            if (params.mApplyStartTransactionOnDraw) {
                if (onDrawTransaction == null) {
                    throw new IllegalArgumentException("Trying to sync a null Transaction");
                }
                mViewHost.getRootSurfaceControl().applyTransactionOnDraw(onDrawTransaction);
            }
            mViewHost.setView(outResult.mRootView, lp);
            Trace.endSection();
        } else {
            Trace.beginSection("CaptionViewHostLayout-relayout");
            if (params.mApplyStartTransactionOnDraw) {
                if (onDrawTransaction == null) {
                    throw new IllegalArgumentException("Trying to sync a null Transaction");
                }
                mViewHost.getRootSurfaceControl().applyTransactionOnDraw(onDrawTransaction);
            }
            mViewHost.relayout(lp);
            Trace.endSection();
        }
        Trace.endSection(); // CaptionViewHostLayout
    }

    private Rect calculateBoundingRect(@NonNull OccludingCaptionElement element,
            int elementWidthPx, @NonNull Rect captionRect) {
        switch (element.mAlignment) {
            case START -> {
                return new Rect(0, 0, elementWidthPx, captionRect.height());
            }
            case END -> {
                return new Rect(captionRect.width() - elementWidthPx, 0,
                        captionRect.width(), captionRect.height());
            }
        }
        throw new IllegalArgumentException("Unexpected alignment " + element.mAlignment);
    }

    /**
     * Checks if task has entered/exited immersive mode and requires a change in caption visibility.
     */
    private void updateCaptionVisibility(View rootView, int displayId) {
        final InsetsState insetsState = mDisplayController.getInsetsState(displayId);
        for (int i = 0; i < insetsState.sourceSize(); i++) {
            final InsetsSource source = insetsState.sourceAt(i);
            if (source.getType() != statusBars()) {
                continue;
            }

            mIsCaptionVisible = source.isVisible();
            setCaptionVisibility(rootView, mIsCaptionVisible);

            return;
        }
    }

    void setTaskDragResizer(TaskDragResizer taskDragResizer) {
        mTaskDragResizer = taskDragResizer;
    }

    private void setCaptionVisibility(View rootView, boolean visible) {
        if (rootView == null) {
            return;
        }
        final int v = visible ? View.VISIBLE : View.GONE;
        final View captionView = rootView.findViewById(getCaptionViewId());
        captionView.setVisibility(v);
    }

    int getCaptionHeightId(@WindowingMode int windowingMode) {
        return Resources.ID_NULL;
    }

    int getCaptionViewId() {
        return Resources.ID_NULL;
    }

    /**
     * Obtains the {@link Display} instance for the display ID in {@link #mTaskInfo} if it exists or
     * registers {@link #mOnDisplaysChangedListener} if it doesn't.
     *
     * @return {@code true} if the {@link Display} instance exists; or {@code false} otherwise
     */
    private boolean obtainDisplayOrRegisterListener() {
        mDisplay = mDisplayController.getDisplay(mTaskInfo.displayId);
        if (mDisplay == null) {
            mDisplayController.addDisplayWindowListener(mOnDisplaysChangedListener);
            return false;
        }
        return true;
    }

    void releaseViews(WindowContainerTransaction wct) {
        if (mViewHost != null) {
            mViewHost.release();
            mViewHost = null;
        }

        mCaptionWindowManager = null;

        final SurfaceControl.Transaction t = mSurfaceControlTransactionSupplier.get();
        boolean released = false;
        if (mCaptionContainerSurface != null) {
            t.remove(mCaptionContainerSurface);
            mCaptionContainerSurface = null;
            released = true;
        }

        if (mDecorationContainerSurface != null) {
            t.remove(mDecorationContainerSurface);
            mDecorationContainerSurface = null;
            released = true;
        }

        if (released) {
            t.apply();
        }

        if (mWindowDecorationInsets != null) {
            mWindowDecorationInsets.remove(wct);
            mWindowDecorationInsets = null;
        }
    }

    @Override
    public void close() {
        Trace.beginSection("WindowDecoration#close");
        mDisplayController.removeDisplayWindowListener(mOnDisplaysChangedListener);
        final WindowContainerTransaction wct = mWindowContainerTransactionSupplier.get();
        releaseViews(wct);
        mTaskOrganizer.applyTransaction(wct);
        mTaskSurface.release();
        Trace.endSection();
    }

    static int loadDimensionPixelSize(Resources resources, int resourceId) {
        if (resourceId == Resources.ID_NULL) {
            return 0;
        }
        return resources.getDimensionPixelSize(resourceId);
    }

    static float loadDimension(Resources resources, int resourceId) {
        if (resourceId == Resources.ID_NULL) {
            return 0;
        }
        return resources.getDimension(resourceId);
    }

    private static SurfaceControl cloneSurfaceControl(SurfaceControl sc,
            Supplier<SurfaceControl> surfaceControlSupplier) {
        final SurfaceControl copy = surfaceControlSupplier.get();
        copy.copyFrom(sc, "WindowDecoration");
        return copy;
    }

    /**
     * Create a window associated with this WindowDecoration.
     * Note that subclass must dispose of this when the task is hidden/closed.
     *
     * @param layoutId     layout to make the window from
     * @param t            the transaction to apply
     * @param xPos         x position of new window
     * @param yPos         y position of new window
     * @param width        width of new window
     * @param height       height of new window
     * @return the {@link AdditionalViewHostViewContainer} that was added.
     */
    AdditionalViewHostViewContainer addWindow(int layoutId, String namePrefix,
            SurfaceControl.Transaction t, SurfaceSyncGroup ssg, int xPos, int yPos,
            int width, int height) {
        final SurfaceControl.Builder builder = mSurfaceControlBuilderSupplier.get();
        SurfaceControl windowSurfaceControl = builder
                .setName(namePrefix + " of Task=" + mTaskInfo.taskId)
                .setContainerLayer()
                .setParent(mDecorationContainerSurface)
                .setCallsite("WindowDecoration.addWindow")
                .build();
        View v = LayoutInflater.from(mDecorWindowContext).inflate(layoutId, null);

        t.setPosition(windowSurfaceControl, xPos, yPos)
                .setWindowCrop(windowSurfaceControl, width, height)
                .show(windowSurfaceControl);
        final WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(width, height, TYPE_APPLICATION,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSPARENT);
        lp.setTitle("Additional window of Task=" + mTaskInfo.taskId);
        lp.setTrustedOverlay();
        WindowlessWindowManager windowManager = new WindowlessWindowManager(mTaskInfo.configuration,
                windowSurfaceControl, null /* hostInputToken */);
        SurfaceControlViewHost viewHost = mSurfaceControlViewHostFactory
                .create(mDecorWindowContext, mDisplay, windowManager);
        ssg.add(viewHost.getSurfacePackage(), () -> viewHost.setView(v, lp));
        return new AdditionalViewHostViewContainer(windowSurfaceControl, viewHost,
                mSurfaceControlTransactionSupplier);
    }

    /**
     * Adds caption inset source to a WCT
     */
    public void addCaptionInset(WindowContainerTransaction wct) {
        final int captionHeightId = getCaptionHeightId(mTaskInfo.getWindowingMode());
        if (captionHeightId == Resources.ID_NULL || !mIsCaptionVisible) {
            return;
        }

        final int captionHeight = loadDimensionPixelSize(mContext.getResources(), captionHeightId);
        final Rect captionInsets = new Rect(0, 0, 0, captionHeight);
        final WindowDecorationInsets newInsets = new WindowDecorationInsets(mTaskInfo.token,
                mOwner, captionInsets, null /* boundingRets */);
        if (!newInsets.equals(mWindowDecorationInsets)) {
            mWindowDecorationInsets = newInsets;
            mWindowDecorationInsets.addOrUpdate(wct);
        }
    }

    static class RelayoutParams {
        RunningTaskInfo mRunningTaskInfo;
        int mLayoutResId;
        int mCaptionHeightId;
        int mCaptionWidthId;
        final List<OccludingCaptionElement> mOccludingCaptionElements = new ArrayList<>();
        int mInputFeatures;

        int mShadowRadiusId;
        int mCornerRadius;

        Configuration mWindowDecorConfig;

        boolean mApplyStartTransactionOnDraw;
        boolean mSetTaskPositionAndCrop;

        void reset() {
            mLayoutResId = Resources.ID_NULL;
            mCaptionHeightId = Resources.ID_NULL;
            mCaptionWidthId = Resources.ID_NULL;
            mOccludingCaptionElements.clear();
            mInputFeatures = 0;

            mShadowRadiusId = Resources.ID_NULL;
            mCornerRadius = 0;

            mApplyStartTransactionOnDraw = false;
            mSetTaskPositionAndCrop = false;
            mWindowDecorConfig = null;
        }

        boolean hasInputFeatureSpy() {
            return (mInputFeatures & WindowManager.LayoutParams.INPUT_FEATURE_SPY) != 0;
        }

        /**
         * Describes elements within the caption bar that could occlude app content, and should be
         * sent as bounding rectangles to the insets system.
         */
        static class OccludingCaptionElement {
            int mWidthResId;
            Alignment mAlignment;

            enum Alignment {
                START, END
            }
        }
    }

    static class RelayoutResult<T extends View & TaskFocusStateConsumer> {
        int mCaptionHeight;
        int mCaptionWidth;
        int mCaptionX;
        final Region mCustomizableCaptionRegion = Region.obtain();
        int mWidth;
        int mHeight;
        T mRootView;

        void reset() {
            mWidth = 0;
            mHeight = 0;
            mCaptionHeight = 0;
            mCaptionWidth = 0;
            mCaptionX = 0;
            mCustomizableCaptionRegion.setEmpty();
            mRootView = null;
        }
    }

    @VisibleForTesting
    public interface SurfaceControlViewHostFactory {
        default SurfaceControlViewHost create(Context c, Display d, WindowlessWindowManager wmm) {
            return new SurfaceControlViewHost(c, d, wmm, "WindowDecoration");
        }
        default SurfaceControlViewHost create(Context c, Display d,
                WindowlessWindowManager wmm, String callsite) {
            return new SurfaceControlViewHost(c, d, wmm, callsite);
        }
    }

    private static class WindowDecorationInsets {
        private static final int INDEX = 0;
        private final WindowContainerToken mToken;
        private final Binder mOwner;
        private final Rect mFrame;
        private final Rect[] mBoundingRects;

        private WindowDecorationInsets(WindowContainerToken token, Binder owner, Rect frame,
                Rect[] boundingRects) {
            mToken = token;
            mOwner = owner;
            mFrame = frame;
            mBoundingRects = boundingRects;
        }

        void addOrUpdate(WindowContainerTransaction wct) {
            wct.addInsetsSource(mToken, mOwner, INDEX, captionBar(), mFrame, mBoundingRects);
            wct.addInsetsSource(mToken, mOwner, INDEX, mandatorySystemGestures(), mFrame,
                    mBoundingRects);
        }

        void remove(WindowContainerTransaction wct) {
            wct.removeInsetsSource(mToken, mOwner, INDEX, captionBar());
            wct.removeInsetsSource(mToken, mOwner, INDEX, mandatorySystemGestures());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WindowDecoration.WindowDecorationInsets that)) return false;
            return Objects.equals(mToken, that.mToken) && Objects.equals(mOwner,
                    that.mOwner) && Objects.equals(mFrame, that.mFrame)
                    && Objects.deepEquals(mBoundingRects, that.mBoundingRects);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mToken, mOwner, mFrame, Arrays.hashCode(mBoundingRects));
        }
    }
}
