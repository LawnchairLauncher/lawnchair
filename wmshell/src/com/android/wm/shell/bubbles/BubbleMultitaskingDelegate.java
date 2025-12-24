/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import static android.app.Notification.BubbleMetadata.FLAG_SUPPRESS_NOTIFICATION;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED;

import static com.android.wm.shell.bubbles.Bubbles.DISMISS_NO_LONGER_BUBBLE;

import android.annotation.BinderThread;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Slog;
import android.window.IMultitaskingControllerCallback;
import android.window.IMultitaskingDelegate;

import com.android.wm.shell.common.ShellExecutor;

import java.util.List;
import java.util.Objects;

/**
 * The implementation of the delegate that accepts requests from the client and interacts with
 * {@link BubbleController} to create, change or remove bubbles.
 */
public class BubbleMultitaskingDelegate extends IMultitaskingDelegate.Stub {

    private static final boolean DEBUG = false;
    private static final String TAG = BubbleMultitaskingDelegate.class.getSimpleName();

    private final BubbleController mController;
    private final ShellExecutor mMainExecutor;
    private final ShellExecutor mBgExecutor;
    private final BubbleData mBubbleData;
    private int mCurrentUserId;
    private IMultitaskingControllerCallback mControllerCallback;

    @SuppressLint("MissingPermission")
    BubbleMultitaskingDelegate(BubbleController controller, BubbleData bubbleData,
            int currentUserId) {
        mController = controller;
        mMainExecutor = controller.getMainExecutor();
        mBgExecutor = controller.getBackgroundExecutor();
        mBubbleData = bubbleData;
        mCurrentUserId = currentUserId;
    }

    void setControllerCallback(IMultitaskingControllerCallback callback) {
        mControllerCallback = callback;
    }

    @BinderThread
    @Override
    public void createBubble(IBinder token, Intent intent, boolean collapsed) {
        if (DEBUG) {
            Slog.d(TAG, "Handling create bubble request");
        }
        Objects.requireNonNull(token);
        Objects.requireNonNull(intent.getComponent());
        Intent bubbleIntent = new Intent(intent);
        bubbleIntent.setPackage(intent.getComponent().getPackageName());
        bubbleIntent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        mMainExecutor.execute(
                () -> {
                    if (getBubbleWithToken(token) != null) {
                        Slog.e(TAG, "Skip creating bubble - found one with the same token.");
                        return;
                    }

                    Bubble b = Bubble.createClientControlledAppBubble(bubbleIntent,
                            new UserHandle(mCurrentUserId), null, token, mMainExecutor,
                            mBgExecutor);
                    if (collapsed) {
                        mController.inflateAndAdd(b, false, false);
                        if (DEBUG) {
                            Slog.d(TAG, "Created a collapsed bubble");
                        }
                    } else {
                        mController.expandStackAndSelectAppBubble(b);
                        if (DEBUG) {
                            Slog.d(TAG, "Created an expanded bubble");
                        }
                    }
                });
    }

    @BinderThread
    @Override
    public void updateBubbleState(IBinder token, boolean collapse) throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Handling bubble state update request.");
        }
        Objects.requireNonNull(token);
        mMainExecutor.execute(
                () -> {
                    if (getBubbleWithToken(token) == null) {
                        Slog.e(TAG, "Skip updating bubble state - none found for the token.");
                        return;
                    }
                    if (collapse) {
                        mController.collapseStack();
                        if (DEBUG) {
                            Slog.d(TAG, "Collapsed bubble stack");
                        }
                    } else {
                        final Bubble bubble = getBubbleWithToken(token);
                        mController.expandStackAndSelectAppBubble(bubble);
                        if (DEBUG) {
                            Slog.d(TAG, "Expanded bubbles");
                        }
                    }
                });
    }

    @BinderThread
    @Override
    public void updateBubbleMessage(IBinder token, String message) throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Handling update bubble request.");
        }
        Objects.requireNonNull(token);
        mMainExecutor.execute(
                () -> {
                    final Bubble bubble = getBubbleWithToken(token);
                    if (bubble == null) {
                        Slog.e(TAG, "Skip updating bubble message - none found for the token.");
                        return;
                    }

                    // Update the flyout message directly.
                    // TODO(b/407149510): this should be refactored into an organized bubble message
                    // update flow, since normally flyout messages are updated through notifications
                    // pipeline and this initial implementation cuts in directly.
                    Bubble.FlyoutMessage bubbleMessage = bubble.getFlyoutMessage();
                    if (bubbleMessage == null) {
                        bubbleMessage = new Bubble.FlyoutMessage();
                        bubble.setFlyoutMessage(bubbleMessage);
                    }
                    bubbleMessage.message = message;
                    bubble.setTextChangedForTest(true);
                    bubble.setSuppressNotification(false);
                    bubble.disable(FLAG_SUPPRESS_NOTIFICATION);
                    mBubbleData.notificationEntryUpdated(bubble,
                            TextUtils.isEmpty(message) /* suppressFlyout */,
                            true /* showInShade */);
                    if (DEBUG) {
                        Slog.d(TAG, "Updated bubble message");
                    }
                });
    }

    @BinderThread
    @Override
    public void removeBubble(IBinder token) throws RemoteException {
        if (DEBUG) {
            Slog.d(TAG, "Handling remove bubble request.");
        }
        Objects.requireNonNull(token);
        mMainExecutor.execute(
                () -> {
                    final Bubble bubble = getBubbleWithToken(token);
                    if (bubble == null) {
                        Slog.e(TAG, "Skip removing bubble - none found for the token.");
                        return;
                    }

                    mController.removeBubble(bubble.getKey(), DISMISS_NO_LONGER_BUBBLE);

                    if (DEBUG) {
                        Slog.d(TAG, "Removed the bubble");
                    }
                });
    }

    void onBubbleRemoved(IBinder clientToken, int reason) {
        if (DEBUG) {
            Slog.d(TAG, "Notifying controller about bubble removal, reason: " + reason);
        }
        mBgExecutor.execute(() -> {
            if (mControllerCallback != null) {
                try {
                    mControllerCallback.onBubbleRemoved(clientToken);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Error reporting bubble removal, reason: " + reason, e);
                }
            }
        });
    }

    void setCurrentUserId(int uid) {
        mCurrentUserId = uid;
    }

    @Nullable
    private Bubble getBubbleWithToken(IBinder token) {
        List<Bubble> bubbleList = mBubbleData.getBubbles();
        for (Bubble b : bubbleList) {
            if (token == b.getClientToken()) {
                return b;
            }
        }
        return null;
    }
}
