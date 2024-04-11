/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.inputconsumers;

import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.media.AudioManager;
import android.media.session.MediaSessionManager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.BaseContainerInterface;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Input consumer for handling touch on the recents/Launcher activity.
 */
public class OverviewInputConsumer<S extends BaseState<S>, T extends RecentsViewContainer>
        implements InputConsumer {

    private final T mContainer;
    private final BaseContainerInterface<?, T> mContainerInterface;
    private final BaseDragLayer mTarget;
    private final InputMonitorCompat mInputMonitor;

    private final int[] mLocationOnScreen = new int[2];

    private final boolean mStartingInActivityBounds;
    private boolean mTargetHandledTouch;
    private boolean mHasSetTouchModeForFirstDPadEvent;
    private boolean mIsWaitingForAttachToWindow;

    public OverviewInputConsumer(GestureState gestureState, T container,
            @Nullable InputMonitorCompat inputMonitor, boolean startingInActivityBounds) {
        mContainer = container;
        mInputMonitor = inputMonitor;
        mStartingInActivityBounds = startingInActivityBounds;
        mContainerInterface = gestureState.getContainerInterface();

        mTarget = container.getDragLayer();
        mTarget.getLocationOnScreen(mLocationOnScreen);
    }

    @Override
    public int getType() {
        return TYPE_OVERVIEW;
    }

    @Override
    public boolean allowInterceptByParent() {
        return !mTargetHandledTouch;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        int flags = ev.getEdgeFlags();
        if (!mStartingInActivityBounds) {
            ev.setEdgeFlags(flags | Utilities.EDGE_NAV_BAR);
        }
        ev.offsetLocation(-mLocationOnScreen[0], -mLocationOnScreen[1]);
        boolean handled = mTarget.proxyTouchEvent(ev, mStartingInActivityBounds);
        ev.offsetLocation(mLocationOnScreen[0], mLocationOnScreen[1]);
        ev.setEdgeFlags(flags);

        if (!mTargetHandledTouch && handled) {
            mTargetHandledTouch = true;
            if (!mStartingInActivityBounds) {
                mContainerInterface.closeOverlay();
                TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
            }
            if (mInputMonitor != null) {
                TestLogging.recordEvent(TestProtocol.SEQUENCE_PILFER, "pilferPointers");
                mInputMonitor.pilferPointers();
            }
        }
        if (mHasSetTouchModeForFirstDPadEvent) {
            mContainer.getRootView().clearFocus();
        }
    }

    @Override
    public void onHoverEvent(MotionEvent ev) {
        mContainer.dispatchGenericMotionEvent(ev);
    }

    @Override
    public void onKeyEvent(KeyEvent ev) {
        switch (ev.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                MediaSessionManager mgr = mContainer.asContext()
                        .getSystemService(MediaSessionManager.class);
                mgr.dispatchVolumeKeyEventAsSystemService(ev,
                        AudioManager.USE_DEFAULT_STREAM_TYPE);
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mHasSetTouchModeForFirstDPadEvent) {
                    break;
                }
                View viewRoot = mContainer.getRootView();
                if (viewRoot.isAttachedToWindow()) {
                    setTouchModeChanged(viewRoot);
                    break;
                }
                if (mIsWaitingForAttachToWindow) {
                    break;
                }
                mIsWaitingForAttachToWindow = true;
                viewRoot.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View view) {
                        view.removeOnAttachStateChangeListener(this);
                        mIsWaitingForAttachToWindow = false;
                        setTouchModeChanged(viewRoot);
                    }

                    @Override
                    public void onViewDetachedFromWindow(View view) {
                        // Do nothing
                    }
                });
                break;
            default:
                break;
        }
        mContainer.dispatchKeyEvent(ev);
    }

    private void setTouchModeChanged(@NonNull View viewRoot) {
        // When Overview is launched via meta+tab or swipe up from an app, the touch
        // mode somehow is not changed to false by the Android framework. The
        // subsequent key events (e.g. DPAD_LEFT, DPAD_RIGHT) can only be dispatched
        // to focused views, while focus can only be requested in
        // {@link View#requestFocusNoSearch(int, Rect)} when touch mode is false. To
        // note, here we launch overview with live tile.
        mHasSetTouchModeForFirstDPadEvent = true;
        viewRoot.getViewRootImpl().touchModeChanged(false);
    }
}

