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

package com.android.wm.shell.common;

import android.Manifest;
import android.os.IBinder;
import android.util.Slog;

import java.util.function.Consumer;

/**
 * An interface for binders which can be registered to be sent to other processes.
 */
public interface ExternalInterfaceBinder {
    /**
     * Invalidates this binder (detaches it from the controller it would call).
     */
    void invalidate();

    /**
     * Returns the IBinder to send.
     */
    IBinder asBinder();

    /**
     * Checks that the caller has the MANAGE_ACTIVITY_TASKS permission and executes the given
     * callback.
     */
    default <T> void executeRemoteCallWithTaskPermission(RemoteCallable<T> controllerInstance,
            String log, Consumer<T> callback) {
        executeRemoteCallWithTaskPermission(controllerInstance, log, callback,
                false /* blocking */);
    }

    /**
     * Checks that the caller has the MANAGE_ACTIVITY_TASKS permission and executes the given
     * callback.
     */
    default <T> void executeRemoteCallWithTaskPermission(RemoteCallable<T> controllerInstance,
            String log, Consumer<T> callback, boolean blocking) {
        if (controllerInstance == null) return;

        final RemoteCallable<T> controller = controllerInstance;
        controllerInstance.getContext().enforceCallingPermission(
                Manifest.permission.MANAGE_ACTIVITY_TASKS, log);
        if (blocking) {
            try {
                controllerInstance.getRemoteCallExecutor().executeBlocking(() -> {
                    callback.accept((T) controller);
                });
            } catch (InterruptedException e) {
                Slog.e("ExternalInterfaceBinder", "Remote call failed", e);
            }
        } else {
            controllerInstance.getRemoteCallExecutor().execute(() -> {
                callback.accept((T) controller);
            });
        }
    }
}
