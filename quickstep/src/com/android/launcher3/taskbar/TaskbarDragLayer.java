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

import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TASKBAR_NAVBAR_UNIFICATION;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.media.permission.SafeCloseable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.android.app.viewcapture.ViewCaptureFactory;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.views.BaseDragLayer;

/**
 * Top-level ViewGroup that hosts the TaskbarView as well as Views created by it such as Folder.
 */
public class TaskbarDragLayer extends BaseDragLayer<TaskbarActivityContext> {

    private static final int INDEX_ALL_OTHER_STATES = 0;
    private static final int INDEX_STASH_ANIM = 1;
    private static final int INDEX_COUNT = 2;

    private static final FloatProperty<TaskbarDragLayer> BG_ALPHA =
            new FloatProperty<>("taskbarBgAlpha") {
                @Override
                public void setValue(TaskbarDragLayer dragLayer, float alpha) {
                    dragLayer.mBackgroundRenderer.getPaint().setAlpha((int) (alpha * 255));
                    dragLayer.invalidate();
                }

                @Override
                public Float get(TaskbarDragLayer dragLayer) {
                    return dragLayer.mBackgroundRenderer.getPaint().getAlpha() / 255f;
                }
            };


    private final TaskbarBackgroundRenderer mBackgroundRenderer;
    private final ViewTreeObserver.OnComputeInternalInsetsListener mTaskbarInsetsComputer =
            this::onComputeTaskbarInsets;

    // Initialized in init.
    private TaskbarDragLayerController.TaskbarDragLayerCallbacks mControllerCallbacks;
    private SafeCloseable mViewCaptureCloseable;

    private float mTaskbarBackgroundOffset;
    private float mTaskbarBackgroundProgress;
    private boolean mIsAnimatingTaskbarPinning = false;

    private final MultiPropertyFactory<TaskbarDragLayer> mTaskbarBackgroundAlpha;

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

        mTaskbarBackgroundAlpha = new MultiPropertyFactory<>(this, BG_ALPHA, INDEX_COUNT,
                (a, b) -> a * b, 1f);
        mTaskbarBackgroundAlpha.get(INDEX_ALL_OTHER_STATES).setValue(0);
        mTaskbarBackgroundAlpha.get(INDEX_STASH_ANIM).setValue(
                enableScalingRevealHomeAnimation() && DisplayController.isTransientTaskbar(context)
                        ? 0
                        : 1);
    }

    public void init(TaskbarDragLayerController.TaskbarDragLayerCallbacks callbacks) {
        mControllerCallbacks = callbacks;
        mBackgroundRenderer.updateStashedHandleWidth(mActivity, getResources());
        recreateControllers();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (insets != null) {
            WindowInsetsCompat insetsCompat = WindowInsetsCompat.toWindowInsetsCompat(insets, this);
            Insets imeInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.ime());
            if (imeInsets != null) {
                mControllerCallbacks.onImeInsetChanged();
            }
        }
        return insets;
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
        onDestroy(!ENABLE_TASKBAR_NAVBAR_UNIFICATION);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnComputeInternalInsetsListener(mTaskbarInsetsComputer);
        mViewCaptureCloseable = ViewCaptureFactory.getInstance(getContext())
                .startCapture(getRootView(), ".Taskbar");
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mViewCaptureCloseable.close();
        onDestroy(true);
    }

    @Override
    protected boolean isEventWithinSystemGestureRegion(MotionEvent ev) {
        final float x = ev.getX();
        final float y = ev.getY();

        return x >= mSystemGestureRegion.left && x < getWidth() - mSystemGestureRegion.right
                && y >= mSystemGestureRegion.top;
    }

    @Override
    protected boolean canFindActiveController() {
        // Unlike super class, we want to be able to find controllers when touches occur in the
        // gesture area. For example, this allows Folder to close itself when touching the Taskbar.
        return true;
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
        mBackgroundRenderer.setBackgroundProgress(mTaskbarBackgroundProgress);
        mBackgroundRenderer.draw(canvas);
        super.dispatchDraw(canvas);
        mControllerCallbacks.drawDebugUi(canvas);
    }

    /**
     * Sets animation boolean when taskbar pinning animation starts or stops.
     */
    public void setAnimatingTaskbarPinning(boolean animatingTaskbarPinning) {
        mIsAnimatingTaskbarPinning = animatingTaskbarPinning;
        mBackgroundRenderer.setAnimatingPinning(mIsAnimatingTaskbarPinning);
    }

    protected MultiProperty getBackgroundRendererAlpha() {
        return mTaskbarBackgroundAlpha.get(INDEX_ALL_OTHER_STATES);
    }

    protected MultiProperty getBackgroundRendererAlphaForStash() {
        return mTaskbarBackgroundAlpha.get(INDEX_STASH_ANIM);
    }

    /**
     * Sets the value for taskbar background switching between persistent and transient backgrounds.
     * @param progress 0 is transient background, 1 is persistent background.
     */
    protected void setTaskbarBackgroundProgress(float progress) {
        mTaskbarBackgroundProgress = progress;
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

    /*
     * Sets the translation of the background during the spring on stash animation.
     */
    protected void setBackgroundTranslationYForStash(float translationY) {
        mBackgroundRenderer.setTranslationYForStash(translationY);
        invalidate();
    }

    /** Returns the bounds in DragLayer coordinates of where the transient background was drawn. */
    protected RectF getLastDrawnTransientRect() {
        return mBackgroundRenderer.getLastDrawnTransientRect();
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
            if (topView != null && topView.canHandleBack()) {
                topView.onBackInvoked();
                // Handled by the floating view.
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Sets the width percentage to inset the transient taskbar's background from the left and from
     * the right.
     */
    public void setBackgroundHorizontalInsets(float insetPercentage) {
        mBackgroundRenderer.setBackgroundHorizontalInsets(insetPercentage);
        invalidate();
    }
}
