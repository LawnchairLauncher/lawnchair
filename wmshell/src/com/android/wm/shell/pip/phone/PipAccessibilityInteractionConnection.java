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
package com.android.wm.shell.pip.phone;

import static com.android.wm.shell.common.pip.PipBoundsState.STASH_TYPE_NONE;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.MagnificationSpec;
import android.view.SurfaceControl;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.accessibility.IAccessibilityInteractionConnection;
import android.view.accessibility.IAccessibilityInteractionConnectionCallback;
import android.window.ScreenCapture;

import androidx.annotation.BinderThread;

import com.android.wm.shell.R;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipSnapAlgorithm;
import com.android.wm.shell.pip.PipTaskOrganizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Expose the touch actions to accessibility as if this object were a window with a single view.
 * That pseudo-view exposes all of the actions this object can perform.
 */
public class PipAccessibilityInteractionConnection {

    public interface AccessibilityCallbacks {
        void onAccessibilityShowMenu();
    }

    private static final long ACCESSIBILITY_NODE_ID = 1;
    private List<AccessibilityNodeInfo> mAccessibilityNodeInfoList;

    private final Context mContext;
    private final ShellExecutor mMainExcutor;
    private final @NonNull PipBoundsState mPipBoundsState;
    private final PipMotionHelper mMotionHelper;
    private final PipTaskOrganizer mTaskOrganizer;
    private final PipSnapAlgorithm mSnapAlgorithm;
    private final Runnable mUpdateMovementBoundCallback;
    private final Runnable mUnstashCallback;
    private final AccessibilityCallbacks mCallbacks;
    private final IAccessibilityInteractionConnection mConnectionImpl;

    private final Rect mNormalBounds = new Rect();
    private final Rect mExpandedBounds = new Rect();
    private final Rect mNormalMovementBounds = new Rect();
    private final Rect mExpandedMovementBounds = new Rect();
    private Rect mTmpBounds = new Rect();

    public PipAccessibilityInteractionConnection(Context context,
            @NonNull PipBoundsState pipBoundsState, PipMotionHelper motionHelper,
            PipTaskOrganizer taskOrganizer, PipSnapAlgorithm snapAlgorithm,
            AccessibilityCallbacks callbacks, Runnable updateMovementBoundCallback,
            Runnable unstashCallback, ShellExecutor mainExcutor) {
        mContext = context;
        mMainExcutor = mainExcutor;
        mPipBoundsState = pipBoundsState;
        mMotionHelper = motionHelper;
        mTaskOrganizer = taskOrganizer;
        mSnapAlgorithm = snapAlgorithm;
        mUpdateMovementBoundCallback = updateMovementBoundCallback;
        mUnstashCallback = unstashCallback;
        mCallbacks = callbacks;
        mConnectionImpl = new PipAccessibilityInteractionConnectionImpl();
    }

    public void register(AccessibilityManager am) {
        am.setPictureInPictureActionReplacingConnection(mConnectionImpl);
    }

    private void findAccessibilityNodeInfoByAccessibilityId(long accessibilityNodeId,
            Region interactiveRegion, int interactionId,
            IAccessibilityInteractionConnectionCallback callback, int flags,
            int interrogatingPid, long interrogatingTid, MagnificationSpec spec, Bundle args) {
        try {
            callback.setFindAccessibilityNodeInfosResult(
                    (accessibilityNodeId == AccessibilityNodeInfo.ROOT_NODE_ID)
                            ? getNodeList() : null, interactionId);
        } catch (RemoteException re) {
                /* best effort - ignore */
        }
    }

    private void performAccessibilityAction(long accessibilityNodeId, int action,
            Bundle arguments, int interactionId,
            IAccessibilityInteractionConnectionCallback callback, int flags,
            int interrogatingPid, long interrogatingTid) {
        // We only support one view. A request for anything else is invalid
        boolean result = false;
        if (accessibilityNodeId == AccessibilityNodeInfo.ROOT_NODE_ID) {

            // R constants are not final so this cannot be put in the switch-case.
            if (action == R.id.action_pip_resize) {
                if (mPipBoundsState.getBounds().width() == mNormalBounds.width()
                        && mPipBoundsState.getBounds().height() == mNormalBounds.height()) {
                    setToExpandedBounds();
                } else {
                    setToNormalBounds();
                }
                result = true;
            } else if (action == R.id.action_pip_stash) {
                mMotionHelper.animateToStashedClosestEdge();
                result = true;
            } else if (action == R.id.action_pip_unstash) {
                mUnstashCallback.run();
                mPipBoundsState.setStashed(STASH_TYPE_NONE);
                result = true;
            } else {
                switch (action) {
                    case AccessibilityNodeInfo.ACTION_CLICK:
                        mCallbacks.onAccessibilityShowMenu();
                        result = true;
                        break;
                    case AccessibilityNodeInfo.ACTION_DISMISS:
                        mMotionHelper.dismissPip();
                        result = true;
                        break;
                    case com.android.internal.R.id.accessibilityActionMoveWindow:
                        int newX = arguments.getInt(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_MOVE_WINDOW_X);
                        int newY = arguments.getInt(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_MOVE_WINDOW_Y);
                        Rect pipBounds = new Rect();
                        pipBounds.set(mPipBoundsState.getBounds());
                        mTmpBounds.offsetTo(newX, newY);
                        mMotionHelper.movePip(mTmpBounds);
                        result = true;
                        break;
                    case AccessibilityNodeInfo.ACTION_EXPAND:
                        mMotionHelper.expandLeavePip(false /* skipAnimation */);
                        result = true;
                        break;
                    default:
                        // Leave result as false
                }
            }
        }
        try {
            callback.setPerformAccessibilityActionResult(result, interactionId);
        } catch (RemoteException re) {
                /* best effort - ignore */
        }
    }

    private void setToExpandedBounds() {
        float savedSnapFraction = mSnapAlgorithm.getSnapFraction(
                mPipBoundsState.getBounds(), mNormalMovementBounds);
        mSnapAlgorithm.applySnapFraction(mExpandedBounds, mExpandedMovementBounds,
                savedSnapFraction);
        mTaskOrganizer.scheduleFinishResizePip(mExpandedBounds, (Rect bounds) -> {
            mMotionHelper.synchronizePinnedStackBounds();
            mUpdateMovementBoundCallback.run();
        });
    }

    private void setToNormalBounds() {
        float savedSnapFraction = mSnapAlgorithm.getSnapFraction(
                mPipBoundsState.getBounds(), mExpandedMovementBounds);
        mSnapAlgorithm.applySnapFraction(mNormalBounds, mNormalMovementBounds, savedSnapFraction);
        mTaskOrganizer.scheduleFinishResizePip(mNormalBounds, (Rect bounds) -> {
            mMotionHelper.synchronizePinnedStackBounds();
            mUpdateMovementBoundCallback.run();
        });
    }

    private void findAccessibilityNodeInfosByViewId(long accessibilityNodeId,
            String viewId, Region interactiveRegion, int interactionId,
            IAccessibilityInteractionConnectionCallback callback, int flags,
            int interrogatingPid, long interrogatingTid, MagnificationSpec spec) {
        // We have no view with a proper ID
        try {
            callback.setFindAccessibilityNodeInfoResult(null, interactionId);
        } catch (RemoteException re) {
            /* best effort - ignore */
        }
    }

    private void findAccessibilityNodeInfosByText(long accessibilityNodeId, String text,
            Region interactiveRegion, int interactionId,
            IAccessibilityInteractionConnectionCallback callback, int flags,
            int interrogatingPid, long interrogatingTid, MagnificationSpec spec) {
        // We have no view with text
        try {
            callback.setFindAccessibilityNodeInfoResult(null, interactionId);
        } catch (RemoteException re) {
            /* best effort - ignore */
        }
    }

    private void findFocus(long accessibilityNodeId, int focusType, Region interactiveRegion,
            int interactionId, IAccessibilityInteractionConnectionCallback callback, int flags,
            int interrogatingPid, long interrogatingTid, MagnificationSpec spec) {
        // We have no view that can take focus
        try {
            callback.setFindAccessibilityNodeInfoResult(null, interactionId);
        } catch (RemoteException re) {
            /* best effort - ignore */
        }
    }

    private void focusSearch(long accessibilityNodeId, int direction, Region interactiveRegion,
            int interactionId, IAccessibilityInteractionConnectionCallback callback, int flags,
            int interrogatingPid, long interrogatingTid, MagnificationSpec spec) {
        // We have no view that can take focus
        try {
            callback.setFindAccessibilityNodeInfoResult(null, interactionId);
        } catch (RemoteException re) {
            /* best effort - ignore */
        }
    }

    /**
     * Update the normal and expanded bounds so they can be used for Resize.
     */
    void onMovementBoundsChanged(Rect normalBounds, Rect expandedBounds, Rect normalMovementBounds,
            Rect expandedMovementBounds) {
        mNormalBounds.set(normalBounds);
        mExpandedBounds.set(expandedBounds);
        mNormalMovementBounds.set(normalMovementBounds);
        mExpandedMovementBounds.set(expandedMovementBounds);
    }

    /**
     * Update the Root node with PIP Accessibility action items.
     */
    public static AccessibilityNodeInfo obtainRootAccessibilityNodeInfo(Context context) {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setSourceNodeId(AccessibilityNodeInfo.ROOT_NODE_ID,
                AccessibilityWindowInfo.PICTURE_IN_PICTURE_ACTION_REPLACER_WINDOW_ID);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_MOVE_WINDOW);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_EXPAND);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_pip_resize,
                context.getString(R.string.accessibility_action_pip_resize)));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_pip_stash,
                context.getString(R.string.accessibility_action_pip_stash)));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.action_pip_unstash,
                context.getString(R.string.accessibility_action_pip_unstash)));
        info.setImportantForAccessibility(true);
        info.setClickable(true);
        info.setVisibleToUser(true);
        return info;
    }

    private List<AccessibilityNodeInfo> getNodeList() {
        if (mAccessibilityNodeInfoList == null) {
            mAccessibilityNodeInfoList = new ArrayList<>(1);
        }
        AccessibilityNodeInfo info = obtainRootAccessibilityNodeInfo(mContext);
        mAccessibilityNodeInfoList.clear();
        mAccessibilityNodeInfoList.add(info);
        return mAccessibilityNodeInfoList;
    }

    @BinderThread
    private class PipAccessibilityInteractionConnectionImpl
            extends IAccessibilityInteractionConnection.Stub {
        @Override
        public void findAccessibilityNodeInfoByAccessibilityId(long accessibilityNodeId,
                Region bounds, int interactionId,
                IAccessibilityInteractionConnectionCallback callback, int flags,
                int interrogatingPid, long interrogatingTid, MagnificationSpec spec,
                float[] matrixValues, Bundle arguments) throws RemoteException {
            mMainExcutor.execute(() -> {
                PipAccessibilityInteractionConnection.this
                        .findAccessibilityNodeInfoByAccessibilityId(accessibilityNodeId, bounds,
                                interactionId, callback, flags, interrogatingPid, interrogatingTid,
                                spec, arguments);
            });
        }

        @Override
        public void findAccessibilityNodeInfosByViewId(long accessibilityNodeId, String viewId,
                Region bounds, int interactionId,
                IAccessibilityInteractionConnectionCallback callback, int flags,
                int interrogatingPid, long interrogatingTid, MagnificationSpec spec,
                float[] matrixValues)
                throws RemoteException {
            mMainExcutor.execute(() -> {
                PipAccessibilityInteractionConnection.this.findAccessibilityNodeInfosByViewId(
                        accessibilityNodeId, viewId, bounds, interactionId, callback, flags,
                        interrogatingPid, interrogatingTid, spec);
            });
        }

        @Override
        public void findAccessibilityNodeInfosByText(long accessibilityNodeId, String text,
                Region bounds, int interactionId,
                IAccessibilityInteractionConnectionCallback callback, int flags,
                int interrogatingPid, long interrogatingTid, MagnificationSpec spec,
                float[] matrixValues)
                throws RemoteException {
            mMainExcutor.execute(() -> {
                PipAccessibilityInteractionConnection.this.findAccessibilityNodeInfosByText(
                        accessibilityNodeId, text, bounds, interactionId, callback, flags,
                        interrogatingPid, interrogatingTid, spec);
            });
        }

        @Override
        public void findFocus(long accessibilityNodeId, int focusType, Region bounds,
                int interactionId, IAccessibilityInteractionConnectionCallback callback, int flags,
                int interrogatingPid, long interrogatingTid, MagnificationSpec spec,
                float[] matrixValues)
                throws RemoteException {
            mMainExcutor.execute(() -> {
                PipAccessibilityInteractionConnection.this.findFocus(accessibilityNodeId, focusType,
                        bounds, interactionId, callback, flags, interrogatingPid, interrogatingTid,
                        spec);
            });
        }

        @Override
        public void focusSearch(long accessibilityNodeId, int direction, Region bounds,
                int interactionId, IAccessibilityInteractionConnectionCallback callback, int flags,
                int interrogatingPid, long interrogatingTid, MagnificationSpec spec,
                float[] matrixValues)
                throws RemoteException {
            mMainExcutor.execute(() -> {
                PipAccessibilityInteractionConnection.this.focusSearch(accessibilityNodeId,
                        direction,
                        bounds, interactionId, callback, flags, interrogatingPid, interrogatingTid,
                        spec);
            });
        }

        @Override
        public void performAccessibilityAction(long accessibilityNodeId, int action,
                Bundle arguments, int interactionId,
                IAccessibilityInteractionConnectionCallback callback, int flags,
                int interrogatingPid, long interrogatingTid) throws RemoteException {
            mMainExcutor.execute(() -> {
                PipAccessibilityInteractionConnection.this.performAccessibilityAction(
                        accessibilityNodeId, action, arguments, interactionId, callback, flags,
                        interrogatingPid, interrogatingTid);
            });
        }

        @Override
        public void takeScreenshotOfWindow(int interactionId,
                ScreenCapture.ScreenCaptureListener listener,
                IAccessibilityInteractionConnectionCallback callback) throws RemoteException {
            // AbstractAccessibilityServiceConnection uses the standard
            // IAccessibilityInteractionConnection for takeScreenshotOfWindow for Pip windows,
            // so do nothing here.
        }

        @Override
        public void clearAccessibilityFocus() throws RemoteException {
            // Do nothing
        }

        @Override
        public void notifyOutsideTouch() throws RemoteException {
            // Do nothing
        }

        @Override
        public void attachAccessibilityOverlayToWindow(
                SurfaceControl sc,
                int interactionId,
                IAccessibilityInteractionConnectionCallback callback) {}
}
}