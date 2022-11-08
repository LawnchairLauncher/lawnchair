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
package com.android.launcher3.taskbar;

import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_BACK;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.views.BaseDragLayer;

/**
 * Top-level ViewGroup that hosts the TaskbarView as well as Views created by it such as Folder.
 */
public class TaskbarDragLayer extends BaseDragLayer<TaskbarActivityContext> {

    private final TaskbarBackgroundRenderer mBackgroundRenderer;
    private final ViewTreeObserver.OnComputeInternalInsetsListener mTaskbarInsetsComputer =
            this::onComputeTaskbarInsets;

    // Initialized in init.
    private TaskbarDragLayerController.TaskbarDragLayerCallbacks mControllerCallbacks;

    private float mTaskbarBackgroundOffset;

    public TaskbarDragLayer(@NonNull Context context) {
        this(context, null);
    }

    public TaskbarDragLayer(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarDragLayer(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TaskbarDragLayer(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, 1 /* alphaChannelCount */);
        mBackgroundRenderer = new TaskbarBackgroundRenderer(mActivity);
        mBackgroundRenderer.getPaint().setAlpha(0);
    }

    public void init(TaskbarDragLayerController.TaskbarDragLayerCallbacks callbacks) {
        mControllerCallbacks = callbacks;

        recreateControllers();
    }

    @Override
    public void recreateControllers() {
        mControllers = mControllerCallbacks.getTouchControllers();
    }

    private void onComputeTaskbarInsets(ViewTreeObserver.InternalInsetsInfo insetsInfo) {
        if (mControllerCallbacks != null) {
            mControllerCallbacks.updateInsetsTouchability(insetsInfo);
        }
    }

    protected void onDestroy(boolean forceDestroy) {
        if (forceDestroy) {
            getViewTreeObserver().removeOnComputeInternalInsetsListener(mTaskbarInsetsComputer);
        }
    }

    protected void onDestroy() {
        onDestroy(!TaskbarManager.FLAG_HIDE_NAVBAR_WINDOW);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnComputeInternalInsetsListener(mTaskbarInsetsComputer);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        onDestroy(true);
    }

    @Override
    protected boolean canFindActiveController() {
        // Unlike super class, we want to be able to find controllers when touches occur in the
        // gesture area. For example, this allows Folder to close itself when touching the Taskbar.
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mControllerCallbacks != null) {
            mControllerCallbacks.tryStashBasedOnMotionEvent(ev);
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mControllerCallbacks != null) {
            mControllerCallbacks.tryStashBasedOnMotionEvent(ev);
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        if (mControllerCallbacks != null) {
            mControllerCallbacks.onDragLayerViewRemoved();
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        float backgroundHeight = mControllerCallbacks.getTaskbarBackgroundHeight()
                * (1f - mTaskbarBackgroundOffset);
        mBackgroundRenderer.setBackgroundHeight(backgroundHeight);
        mBackgroundRenderer.draw(canvas);
        super.dispatchDraw(canvas);
    }

    /**
     * Sets the alpha of the background color behind all the Taskbar contents.
     * @param alpha 0 is fully transparent, 1 is fully opaque.
     */
    protected void setTaskbarBackgroundAlpha(float alpha) {
        mBackgroundRenderer.getPaint().setAlpha((int) (alpha * 255));
        invalidate();
    }

    /**
     * Sets the translation of the background color behind all the Taskbar contents.
     * @param offset 0 is fully onscreen, 1 is fully offscreen.
     */
    protected void setTaskbarBackgroundOffset(float offset) {
        mTaskbarBackgroundOffset = offset;
        invalidate();
    }

    /**
     * Sets the roundness of the round corner above Taskbar.
     * @param cornerRoundness 0 has no round corner, 1 has complete round corner.
     */
    protected void setCornerRoundness(float cornerRoundness) {
        mBackgroundRenderer.setCornerRoundness(cornerRoundness);
        invalidate();
    }

    /*
     * Sets the translation of the background during the swipe up gesture.
     */
    protected void setBackgroundTranslationYForSwipe(float translationY) {
        mBackgroundRenderer.setTranslationYForSwipe(translationY);
        invalidate();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        TestLogging.recordMotionEvent(TestProtocol.SEQUENCE_MAIN, "Touch event", ev);
        return super.dispatchTouchEvent(ev);
    }

    /** Called while Taskbar window is focusable, e.g. when pressing back while a folder is open */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == ACTION_UP && event.getKeyCode() == KEYCODE_BACK) {
            AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mActivity);
            if (topView != null && topView.onBackPressed()) {
                // Handled by the floating view.
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
}
