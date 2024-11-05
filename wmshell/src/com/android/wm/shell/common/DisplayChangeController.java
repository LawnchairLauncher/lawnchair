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

package com.android.wm.shell.common;

import android.annotation.Nullable;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Slog;
import android.view.IDisplayChangeWindowCallback;
import android.view.IDisplayChangeWindowController;
import android.view.IWindowManager;
import android.window.DisplayAreaInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.BinderThread;

import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.sysui.ShellInit;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This module deals with display rotations coming from WM. When WM starts a rotation: after it has
 * frozen the screen, it will call into this class. This will then call all registered local
 * controllers and give them a chance to queue up task changes to be applied synchronously with that
 * rotation.
 */
public class DisplayChangeController {
    private static final String TAG = DisplayChangeController.class.getSimpleName();
    private static final String HANDLE_DISPLAY_CHANGE_TRACE_TAG = "HandleRemoteDisplayChange";

    private final ShellExecutor mMainExecutor;
    private final IWindowManager mWmService;
    private final IDisplayChangeWindowController mControllerImpl;

    private final CopyOnWriteArrayList<OnDisplayChangingListener> mDisplayChangeListener =
            new CopyOnWriteArrayList<>();

    public DisplayChangeController(IWindowManager wmService, ShellInit shellInit,
            ShellExecutor mainExecutor) {
        mMainExecutor = mainExecutor;
        mWmService = wmService;
        mControllerImpl = new DisplayChangeWindowControllerImpl();
        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        try {
            mWmService.setDisplayChangeWindowController(mControllerImpl);
        } catch (RemoteException e) {
            throw new RuntimeException("Unable to register rotation controller");
        }
    }

    /**
     * Adds a display rotation controller.
     */
    public void addDisplayChangeListener(OnDisplayChangingListener listener) {
        mDisplayChangeListener.add(listener);
    }

    /**
     * Removes a display rotation controller.
     */
    public void removeDisplayChangeListener(OnDisplayChangingListener listener) {
        mDisplayChangeListener.remove(listener);
    }

    /** Query all listeners for changes that should happen on display change. */
    void dispatchOnDisplayChange(WindowContainerTransaction outWct, int displayId,
            int fromRotation, int toRotation, DisplayAreaInfo newDisplayAreaInfo) {
        if (Trace.isTagEnabled(Trace.TRACE_TAG_WINDOW_MANAGER)) {
            Trace.beginSection("dispatchOnDisplayChange");
        }
        for (OnDisplayChangingListener c : mDisplayChangeListener) {
            c.onDisplayChange(displayId, fromRotation, toRotation, newDisplayAreaInfo, outWct);
        }
        if (Trace.isTagEnabled(Trace.TRACE_TAG_WINDOW_MANAGER)) {
            Trace.endSection();
        }
    }

    private void onDisplayChange(int displayId, int fromRotation, int toRotation,
            DisplayAreaInfo newDisplayAreaInfo, IDisplayChangeWindowCallback callback) {
        WindowContainerTransaction t = new WindowContainerTransaction();
        dispatchOnDisplayChange(t, displayId, fromRotation, toRotation, newDisplayAreaInfo);
        try {
            callback.continueDisplayChange(t);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed to continue handling display change", e);
        } finally {
            if (Trace.isTagEnabled(Trace.TRACE_TAG_WINDOW_MANAGER)) {
                Trace.endAsyncSection(HANDLE_DISPLAY_CHANGE_TRACE_TAG, callback.hashCode());
            }
        }
    }

    @BinderThread
    private class DisplayChangeWindowControllerImpl
            extends IDisplayChangeWindowController.Stub {
        @Override
        public void onDisplayChange(int displayId, int fromRotation, int toRotation,
                DisplayAreaInfo newDisplayAreaInfo, IDisplayChangeWindowCallback callback) {
            if (Trace.isTagEnabled(Trace.TRACE_TAG_WINDOW_MANAGER)) {
                Trace.beginAsyncSection(HANDLE_DISPLAY_CHANGE_TRACE_TAG, callback.hashCode());
            }
            mMainExecutor.execute(() -> DisplayChangeController.this
                    .onDisplayChange(displayId, fromRotation, toRotation,
                            newDisplayAreaInfo, callback));
        }
    }

    /**
     * Give a listener a chance to queue up configuration changes to execute as part of a
     * display rotation. The contents of {@link #onDisplayChange} must run synchronously.
     */
    @ShellMainThread
    public interface OnDisplayChangingListener {
        /**
         * Called before the display size has changed.
         * Contents of this method must run synchronously.
         * @param displayId display id of the display that is under the change
         * @param fromRotation rotation before the change
         * @param toRotation rotation after the change
         * @param newDisplayAreaInfo display area info after applying the update
         * @param t A task transaction to populate.
         */
        void onDisplayChange(int displayId, int fromRotation, int toRotation,
                @Nullable DisplayAreaInfo newDisplayAreaInfo, WindowContainerTransaction t);
    }
}
