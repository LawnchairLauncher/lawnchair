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

package com.android.wm.shell.windowdecor;

import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getFineResizeCornerSize;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getLargeResizeCornerSize;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.getResizeEdgeHandleSize;

import android.annotation.NonNull;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.WindowConfiguration;
import android.app.WindowConfiguration.WindowingMode;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Handler;
import android.util.Size;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewConfiguration;
import android.window.WindowContainerTransaction;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;

/**
 * Defines visuals and behaviors of a window decoration of a caption bar and shadows. It works with
 * {@link CaptionWindowDecorViewModel}. The caption bar contains a back button, minimize button,
 * maximize button and close button.
 */
public class CaptionWindowDecoration extends WindowDecoration<WindowDecorLinearLayout> {
    private final Handler mHandler;
    private final Choreographer mChoreographer;
    private final SyncTransactionQueue mSyncQueue;

    private View.OnClickListener mOnCaptionButtonClickListener;
    private View.OnTouchListener mOnCaptionTouchListener;
    private DragPositioningCallback mDragPositioningCallback;
    private DragResizeInputListener mDragResizeListener;
    private DragDetector mDragDetector;

    private RelayoutParams mRelayoutParams = new RelayoutParams();
    private final RelayoutResult<WindowDecorLinearLayout> mResult =
            new RelayoutResult<>();

    CaptionWindowDecoration(
            Context context,
            DisplayController displayController,
            ShellTaskOrganizer taskOrganizer,
            RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            Handler handler,
            Choreographer choreographer,
            SyncTransactionQueue syncQueue) {
        super(context, displayController, taskOrganizer, taskInfo, taskSurface);
        mHandler = handler;
        mChoreographer = choreographer;
        mSyncQueue = syncQueue;
    }

    void setCaptionListeners(
            View.OnClickListener onCaptionButtonClickListener,
            View.OnTouchListener onCaptionTouchListener) {
        mOnCaptionButtonClickListener = onCaptionButtonClickListener;
        mOnCaptionTouchListener = onCaptionTouchListener;
    }

    void setDragPositioningCallback(DragPositioningCallback dragPositioningCallback) {
        mDragPositioningCallback = dragPositioningCallback;
    }

    @Override
    @NonNull
    Rect calculateValidDragArea() {
        final Context displayContext = mDisplayController.getDisplayContext(mTaskInfo.displayId);
        if (displayContext == null) return new Rect();
        final int leftButtonsWidth = loadDimensionPixelSize(mContext.getResources(),
                R.dimen.caption_left_buttons_width);

        // On a smaller screen, don't require as much empty space on screen, as offscreen
        // drags will be restricted too much.
        final int requiredEmptySpaceId = displayContext
                .getResources().getConfiguration().smallestScreenWidthDp >= 600
                ? R.dimen.freeform_required_visible_empty_space_in_header :
                R.dimen.small_screen_required_visible_empty_space_in_header;
        final int requiredEmptySpace = loadDimensionPixelSize(mContext.getResources(),
                requiredEmptySpaceId);

        final int rightButtonsWidth = loadDimensionPixelSize(mContext.getResources(),
                R.dimen.caption_right_buttons_width);
        final int taskWidth = mTaskInfo.configuration.windowConfiguration.getBounds().width();
        final DisplayLayout layout = mDisplayController.getDisplayLayout(mTaskInfo.displayId);
        final int displayWidth = layout.width();
        final Rect stableBounds = new Rect();
        layout.getStableBounds(stableBounds);
        return new Rect(
                determineMinX(leftButtonsWidth, rightButtonsWidth, requiredEmptySpace,
                        taskWidth),
                stableBounds.top,
                determineMaxX(leftButtonsWidth, rightButtonsWidth, requiredEmptySpace, taskWidth,
                        displayWidth),
                determineMaxY(requiredEmptySpace, stableBounds));
    }


    /**
     * Determine the lowest x coordinate of a freeform task. Used for restricting drag inputs.
     */
    private int determineMinX(int leftButtonsWidth, int rightButtonsWidth, int requiredEmptySpace,
            int taskWidth) {
        // Do not let apps with < 48dp empty header space go off the left edge at all.
        if (leftButtonsWidth + rightButtonsWidth + requiredEmptySpace > taskWidth) {
            return 0;
        }
        return -taskWidth + requiredEmptySpace + rightButtonsWidth;
    }

    /**
     * Determine the highest x coordinate of a freeform task. Used for restricting drag inputs.
     */
    private int determineMaxX(int leftButtonsWidth, int rightButtonsWidth, int requiredEmptySpace,
            int taskWidth, int displayWidth) {
        // Do not let apps with < 48dp empty header space go off the right edge at all.
        if (leftButtonsWidth + rightButtonsWidth + requiredEmptySpace > taskWidth) {
            return displayWidth - taskWidth;
        }
        return displayWidth - requiredEmptySpace - leftButtonsWidth;
    }

    /**
     * Determine the highest y coordinate of a freeform task. Used for restricting drag inputs.
     */
    private int determineMaxY(int requiredEmptySpace, Rect stableBounds) {
        return stableBounds.bottom - requiredEmptySpace;
    }


    void setDragDetector(DragDetector dragDetector) {
        mDragDetector = dragDetector;
        mDragDetector.setTouchSlop(ViewConfiguration.get(mContext).getScaledTouchSlop());
    }

    @Override
    void relayout(RunningTaskInfo taskInfo) {
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        // The crop and position of the task should only be set when a task is fluid resizing. In
        // all other cases, it is expected that the transition handler positions and crops the task
        // in order to allow the handler time to animate before the task before the final
        // position and crop are set.
        final boolean shouldSetTaskPositionAndCrop = mTaskDragResizer.isResizingOrAnimating();
        // Use |applyStartTransactionOnDraw| so that the transaction (that applies task crop) is
        // synced with the buffer transaction (that draws the View). Both will be shown on screen
        // at the same, whereas applying them independently causes flickering. See b/270202228.
        relayout(taskInfo, t, t, true /* applyStartTransactionOnDraw */,
                shouldSetTaskPositionAndCrop);
    }

    void relayout(RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            boolean applyStartTransactionOnDraw, boolean setTaskCropAndPosition) {
        final int shadowRadiusID = taskInfo.isFocused
                ? R.dimen.freeform_decor_shadow_focused_thickness
                : R.dimen.freeform_decor_shadow_unfocused_thickness;
        final boolean isFreeform =
                taskInfo.getWindowingMode() == WindowConfiguration.WINDOWING_MODE_FREEFORM;
        final boolean isDragResizeable = isFreeform && taskInfo.isResizeable;

        final WindowDecorLinearLayout oldRootView = mResult.mRootView;
        final SurfaceControl oldDecorationSurface = mDecorationContainerSurface;
        final WindowContainerTransaction wct = new WindowContainerTransaction();

        mRelayoutParams.reset();
        mRelayoutParams.mRunningTaskInfo = taskInfo;
        mRelayoutParams.mLayoutResId = R.layout.caption_window_decor;
        mRelayoutParams.mCaptionHeightId = getCaptionHeightId(taskInfo.getWindowingMode());
        mRelayoutParams.mShadowRadiusId = shadowRadiusID;
        mRelayoutParams.mApplyStartTransactionOnDraw = applyStartTransactionOnDraw;
        mRelayoutParams.mSetTaskPositionAndCrop = setTaskCropAndPosition;

        relayout(mRelayoutParams, startT, finishT, wct, oldRootView, mResult);
        // After this line, mTaskInfo is up-to-date and should be used instead of taskInfo

        mTaskOrganizer.applyTransaction(wct);

        if (mResult.mRootView == null) {
            // This means something blocks the window decor from showing, e.g. the task is hidden.
            // Nothing is set up in this case including the decoration surface.
            return;
        }
        if (oldRootView != mResult.mRootView) {
            setupRootView();
        }

        if (!isDragResizeable) {
            closeDragResizeListener();
            return;
        }

        if (oldDecorationSurface != mDecorationContainerSurface || mDragResizeListener == null) {
            closeDragResizeListener();
            mDragResizeListener = new DragResizeInputListener(
                    mContext,
                    mHandler,
                    mChoreographer,
                    mDisplay.getDisplayId(),
                    mDecorationContainerSurface,
                    mDragPositioningCallback,
                    mSurfaceControlBuilderSupplier,
                    mSurfaceControlTransactionSupplier,
                    mDisplayController);
        }

        final int touchSlop = ViewConfiguration.get(mResult.mRootView.getContext())
                .getScaledTouchSlop();
        mDragDetector.setTouchSlop(touchSlop);

        final Resources res = mResult.mRootView.getResources();
        mDragResizeListener.setGeometry(new DragResizeWindowGeometry(0 /* taskCornerRadius */,
                new Size(mResult.mWidth, mResult.mHeight), getResizeEdgeHandleSize(res),
                getFineResizeCornerSize(res), getLargeResizeCornerSize(res)), touchSlop);
    }

    /**
     * Sets up listeners when a new root view is created.
     */
    private void setupRootView() {
        final View caption = mResult.mRootView.findViewById(R.id.caption);
        caption.setOnTouchListener(mOnCaptionTouchListener);
        final View close = caption.findViewById(R.id.close_window);
        close.setOnClickListener(mOnCaptionButtonClickListener);
        final View back = caption.findViewById(R.id.back_button);
        back.setOnClickListener(mOnCaptionButtonClickListener);
        final View minimize = caption.findViewById(R.id.minimize_window);
        minimize.setOnClickListener(mOnCaptionButtonClickListener);
        final View maximize = caption.findViewById(R.id.maximize_window);
        maximize.setOnClickListener(mOnCaptionButtonClickListener);
    }

    void setCaptionColor(int captionColor) {
        if (mResult.mRootView == null) {
            return;
        }

        final View caption = mResult.mRootView.findViewById(R.id.caption);
        final GradientDrawable captionDrawable = (GradientDrawable) caption.getBackground();
        captionDrawable.setColor(captionColor);

        final int buttonTintColorRes =
                Color.valueOf(captionColor).luminance() < 0.5
                        ? R.color.decor_button_light_color
                        : R.color.decor_button_dark_color;
        final ColorStateList buttonTintColor =
                caption.getResources().getColorStateList(buttonTintColorRes, null /* theme */);

        final View back = caption.findViewById(R.id.back_button);
        final VectorDrawable backBackground = (VectorDrawable) back.getBackground();
        backBackground.setTintList(buttonTintColor);

        final View minimize = caption.findViewById(R.id.minimize_window);
        final VectorDrawable minimizeBackground = (VectorDrawable) minimize.getBackground();
        minimizeBackground.setTintList(buttonTintColor);

        final View maximize = caption.findViewById(R.id.maximize_window);
        final VectorDrawable maximizeBackground = (VectorDrawable) maximize.getBackground();
        maximizeBackground.setTintList(buttonTintColor);

        final View close = caption.findViewById(R.id.close_window);
        final VectorDrawable closeBackground = (VectorDrawable) close.getBackground();
        closeBackground.setTintList(buttonTintColor);
    }

    boolean isHandlingDragResize() {
        return mDragResizeListener != null && mDragResizeListener.isHandlingDragResize();
    }

    private void closeDragResizeListener() {
        if (mDragResizeListener == null) {
            return;
        }
        mDragResizeListener.close();
        mDragResizeListener = null;
    }

    @Override
    public void close() {
        closeDragResizeListener();
        super.close();
    }

    @Override
    int getCaptionHeightId(@WindowingMode int windowingMode) {
        return R.dimen.freeform_decor_caption_height;
    }

    @Override
    int getCaptionViewId() {
        return R.id.caption;
    }
}
