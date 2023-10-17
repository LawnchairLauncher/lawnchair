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
package com.android.launcher3.taskbar.overlay;

import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION;

import android.content.Context;
import android.graphics.Insets;
import android.media.permission.SafeCloseable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;

import androidx.annotation.NonNull;

import com.android.app.viewcapture.SettingsAwareViewCapture;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.BaseDragLayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Root drag layer for the Taskbar overlay window. */
public class TaskbarOverlayDragLayer extends
        BaseDragLayer<TaskbarOverlayContext> implements
        ViewTreeObserver.OnComputeInternalInsetsListener {

    private SafeCloseable mViewCaptureCloseable;
    private final List<OnClickListener> mOnClickListeners = new CopyOnWriteArrayList<>();
    private final TouchController mClickListenerTouchController = new TouchController() {
        @Override
        public boolean onControllerTouchEvent(MotionEvent ev) {
            if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                for (OnClickListener listener : mOnClickListeners) {
                    listener.onClick(TaskbarOverlayDragLayer.this);
                }
            }
            return false;
        }

        @Override
        public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
            for (int i = 0; i < getChildCount(); i++) {
                if (isEventOverView(getChildAt(i), ev)) {
                    return false;
                }
            }
            return true;
        }
    };

    TaskbarOverlayDragLayer(Context context) {
        super(context, null, 1);
        setClipChildren(false);
        recreateControllers();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnComputeInternalInsetsListener(this);
        mViewCaptureCloseable = SettingsAwareViewCapture.getInstance(getContext())
                .startCapture(getRootView(), ".TaskbarOverlay");
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnComputeInternalInsetsListener(this);
        mViewCaptureCloseable.close();
    }

    @Override
    public void recreateControllers() {
        mControllers = mOnClickListeners.isEmpty()
                ? new TouchController[]{mActivity.getDragController()}
                : new TouchController[] {
                        mActivity.getDragController(), mClickListenerTouchController};
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        TestLogging.recordMotionEvent(TestProtocol.SEQUENCE_MAIN, "Touch event", ev);
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == ACTION_UP && event.getKeyCode() == KEYCODE_BACK) {
            AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(mActivity);
            if (topView != null && topView.canHandleBack()) {
                topView.onBackInvoked();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onComputeInternalInsets(ViewTreeObserver.InternalInsetsInfo inoutInfo) {
        if (mActivity.isAnySystemDragInProgress()) {
            inoutInfo.touchableRegion.setEmpty();
            inoutInfo.setTouchableInsets(TOUCHABLE_INSETS_REGION);
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        insets = updateInsetsDueToStashing(insets);
        setInsets(insets.getInsets(WindowInsets.Type.systemBars()).toRect());
        return insets;
    }

    @Override
    public void onViewRemoved(View child) {
        super.onViewRemoved(child);
        mActivity.getOverlayController().maybeCloseWindow();
    }

    /**
     * Adds the given callback to clicks to this drag layer.
     * <p>
     * Clicks are only accepted on this drag layer if they fall within this drag layer's bounds and
     * outside the bounds of all child views.
     * <p>
     * If the click falls within the bounds of a child view, then this callback does not run and
     * that child can optionally handle it.
     */
    private void addOnClickListener(@NonNull OnClickListener listener) {
        boolean wasEmpty = mOnClickListeners.isEmpty();
        mOnClickListeners.add(listener);
        if (wasEmpty) {
            recreateControllers();
        }
    }

    /**
     * Removes the given on click callback.
     * <p>
     * No-op if the callback was never added.
     */
    private void removeOnClickListener(@NonNull OnClickListener listener) {
        boolean wasEmpty = mOnClickListeners.isEmpty();
        mOnClickListeners.remove(listener);
        if (!wasEmpty && mOnClickListeners.isEmpty()) {
            recreateControllers();
        }
    }

    /**
     * Queues the given callback on the next click on this drag layer.
     * <p>
     * Once run, this callback is immediately removed.
     */
    public void runOnClickOnce(@NonNull OnClickListener listener) {
        addOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClick(v);
                removeOnClickListener(this);
            }
        });
    }

    /**
     * Taskbar automatically stashes when opening all apps, but we don't report the insets as
     * changing to avoid moving the underlying app. But internally, the apps view should still
     * layout according to the stashed insets rather than the unstashed insets. So this method
     * does two things:
     * 1) Sets navigationBars bottom inset to stashedHeight.
     * 2) Sets tappableInsets bottom inset to 0.
     */
    private WindowInsets updateInsetsDueToStashing(WindowInsets oldInsets) {
        if (!DisplayController.isTransientTaskbar(mActivity)) {
            return oldInsets;
        }
        WindowInsets.Builder updatedInsetsBuilder = new WindowInsets.Builder(oldInsets);

        Insets oldNavInsets = oldInsets.getInsets(WindowInsets.Type.navigationBars());
        Insets newNavInsets = Insets.of(oldNavInsets.left, oldNavInsets.top, oldNavInsets.right,
                mActivity.getStashedTaskbarHeight());
        updatedInsetsBuilder.setInsets(WindowInsets.Type.navigationBars(), newNavInsets);

        Insets oldTappableInsets = oldInsets.getInsets(WindowInsets.Type.tappableElement());
        Insets newTappableInsets = Insets.of(oldTappableInsets.left, oldTappableInsets.top,
                oldTappableInsets.right, 0);
        updatedInsetsBuilder.setInsets(WindowInsets.Type.tappableElement(), newTappableInsets);

        return updatedInsetsBuilder.build();
    }
}
