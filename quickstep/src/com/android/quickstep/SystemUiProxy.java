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
package com.android.quickstep;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.systemui.shared.recents.IPinnedStackAnimationListener;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.Task;

/**
 * Holds the reference to SystemUI.
 */
public class SystemUiProxy implements ISystemUiProxy {
    private static final String TAG = SystemUiProxy.class.getSimpleName();

    public static final MainThreadInitializedObject<SystemUiProxy> INSTANCE =
            new MainThreadInitializedObject<>(SystemUiProxy::new);

    private ISystemUiProxy mSystemUiProxy;
    private final DeathRecipient mSystemUiProxyDeathRecipient = () -> {
        MAIN_EXECUTOR.execute(() -> setProxy(null));
    };

    // Used to dedupe calls to SystemUI
    private int mLastShelfHeight;
    private boolean mLastShelfVisible;
    private float mLastBackButtonAlpha;
    private boolean mLastBackButtonAnimate;

    // TODO(141886704): Find a way to remove this
    private int mLastSystemUiStateFlags;

    public SystemUiProxy(Context context) {
        // Do nothing
    }

    @Override
    public IBinder asBinder() {
        // Do nothing
        return null;
    }

    public void setProxy(ISystemUiProxy proxy) {
        unlinkToDeath();
        mSystemUiProxy = proxy;
        linkToDeath();
    }

    // TODO(141886704): Find a way to remove this
    public void setLastSystemUiStateFlags(int stateFlags) {
        mLastSystemUiStateFlags = stateFlags;
    }

    // TODO(141886704): Find a way to remove this
    public int getLastSystemUiStateFlags() {
        return mLastSystemUiStateFlags;
    }

    public boolean isActive() {
        return mSystemUiProxy != null;
    }

    private void linkToDeath() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.asBinder().linkToDeath(mSystemUiProxyDeathRecipient, 0 /* flags */);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to link sysui proxy death recipient");
            }
        }
    }

    private void unlinkToDeath() {
        if (mSystemUiProxy != null) {
            mSystemUiProxy.asBinder().unlinkToDeath(mSystemUiProxyDeathRecipient, 0 /* flags */);
        }
    }

    @Override
    public void startScreenPinning(int taskId) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.startScreenPinning(taskId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startScreenPinning", e);
            }
        }
    }

    @Override
    public void onSplitScreenInvoked() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onSplitScreenInvoked();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onSplitScreenInvoked", e);
            }
        }
    }

    @Override
    public void onOverviewShown(boolean fromHome) {
        onOverviewShown(fromHome, TAG);
    }

    public void onOverviewShown(boolean fromHome, String tag) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onOverviewShown(fromHome);
            } catch (RemoteException e) {
                Log.w(tag, "Failed call onOverviewShown from: " + (fromHome ? "home" : "app"), e);
            }
        }
    }

    @Override
    public Rect getNonMinimizedSplitScreenSecondaryBounds() {
        if (mSystemUiProxy != null) {
            try {
                return mSystemUiProxy.getNonMinimizedSplitScreenSecondaryBounds();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call getNonMinimizedSplitScreenSecondaryBounds", e);
            }
        }
        return null;
    }

    @Override
    public void setBackButtonAlpha(float alpha, boolean animate) {
        boolean changed = Float.compare(alpha, mLastBackButtonAlpha) != 0
                || animate != mLastBackButtonAnimate;
        if (mSystemUiProxy != null && changed) {
            mLastBackButtonAlpha = alpha;
            mLastBackButtonAnimate = animate;
            try {
                mSystemUiProxy.setBackButtonAlpha(alpha, animate);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setBackButtonAlpha", e);
            }
        }
    }

    public float getLastBackButtonAlpha() {
        return mLastBackButtonAlpha;
    }

    @Override
    public void setNavBarButtonAlpha(float alpha, boolean animate) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.setNavBarButtonAlpha(alpha, animate);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setNavBarButtonAlpha", e);
            }
        }
    }

    @Override
    public void onStatusBarMotionEvent(MotionEvent event) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onStatusBarMotionEvent(event);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onStatusBarMotionEvent", e);
            }
        }
    }

    @Override
    public void onAssistantProgress(float progress) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onAssistantProgress(progress);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onAssistantProgress with progress: " + progress, e);
            }
        }
    }

    @Override
    public void onAssistantGestureCompletion(float velocity) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onAssistantGestureCompletion(velocity);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onAssistantGestureCompletion", e);
            }
        }
    }

    @Override
    public void startAssistant(Bundle args) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.startAssistant(args);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call startAssistant", e);
            }
        }
    }

    @Override
    public Bundle monitorGestureInput(String name, int displayId) {
        if (mSystemUiProxy != null) {
            try {
                return mSystemUiProxy.monitorGestureInput(name, displayId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call monitorGestureInput: " + name, e);
            }
        }
        return null;
    }

    @Override
    public void notifyAccessibilityButtonClicked(int displayId) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifyAccessibilityButtonClicked(displayId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifyAccessibilityButtonClicked", e);
            }
        }
    }

    @Override
    public void notifyAccessibilityButtonLongClicked() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifyAccessibilityButtonLongClicked();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call notifyAccessibilityButtonLongClicked", e);
            }
        }
    }

    @Override
    public void stopScreenPinning() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.stopScreenPinning();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call stopScreenPinning", e);
            }
        }
    }

    @Override
    public void setShelfHeight(boolean visible, int shelfHeight) {
        boolean changed = visible != mLastShelfVisible || shelfHeight != mLastShelfHeight;
        if (mSystemUiProxy != null && changed) {
            mLastShelfVisible = visible;
            mLastShelfHeight = shelfHeight;
            try {
                mSystemUiProxy.setShelfHeight(visible, shelfHeight);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setShelfHeight visible: " + visible
                        + " height: " + shelfHeight, e);
            }
        }
    }

    @Override
    public void handleImageAsScreenshot(Bitmap bitmap, Rect rect, Insets insets, int i) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.handleImageAsScreenshot(bitmap, rect, insets, i);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call handleImageAsScreenshot", e);
            }
        }
    }

    @Override
    public void setSplitScreenMinimized(boolean minimized) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.setSplitScreenMinimized(minimized);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call stopScreenPinning", e);
            }
        }
    }

    /**
     * Notifies that swipe-to-home action is finished.
     */
    @Override
    public void notifySwipeToHomeFinished() {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.notifySwipeToHomeFinished();
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setPinnedStackAnimationType", e);
            }
        }
    }

    /**
     * Sets listener to get pinned stack animation callbacks.
     */
    @Override
    public void setPinnedStackAnimationListener(IPinnedStackAnimationListener listener) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.setPinnedStackAnimationListener(listener);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call setPinnedStackAnimationListener", e);
            }
        }
    }

    @Override
    public void onQuickSwitchToNewTask(int rotation) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.onQuickSwitchToNewTask(rotation);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call onQuickSwitchToNewTask with arg: " + rotation, e);
            }
        }
    }

    @Override
    public void handleImageBundleAsScreenshot(Bundle screenImageBundle, Rect locationInScreen,
            Insets visibleInsets, Task.TaskKey task) {
        if (mSystemUiProxy != null) {
            try {
                mSystemUiProxy.handleImageBundleAsScreenshot(screenImageBundle, locationInScreen,
                        visibleInsets, task);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed call handleImageBundleAsScreenshot");
            }
        }
    }
}
