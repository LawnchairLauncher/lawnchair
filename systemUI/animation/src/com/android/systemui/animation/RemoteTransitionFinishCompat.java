/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.systemui.animation;

import android.annotation.Nullable;
import android.util.Log;
import android.view.SurfaceControl;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.WindowContainerTransaction;

import java.lang.reflect.InvocationTargetException;

/** Version-tolerant bridge for hidden remote transition finish callback descriptors. */
public final class RemoteTransitionFinishCompat {
    private static final String TAG = "RemoteTransitionFinish";

    private RemoteTransitionFinishCompat() {}

    public static void finish(@Nullable IRemoteTransitionFinishedCallback callback,
            @Nullable WindowContainerTransaction wct,
            @Nullable SurfaceControl.Transaction transaction) {
        if (callback == null) {
            return;
        }
        Throwable failure = invoke(callback, wct, transaction);
        if (failure == null) {
            return;
        }
        failure = invoke(callback, transaction);
        if (failure == null) {
            return;
        }
        failure = invoke(callback, wct);
        if (failure != null) {
            Log.e(TAG, "Failed to finish remote transition", failure);
        }
    }

    private static Throwable invoke(IRemoteTransitionFinishedCallback callback,
            WindowContainerTransaction wct, SurfaceControl.Transaction transaction) {
        try {
            IRemoteTransitionFinishedCallback.class
                    .getMethod("onTransitionFinished",
                            WindowContainerTransaction.class,
                            SurfaceControl.Transaction.class)
                    .invoke(callback, wct, transaction);
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return unwrap(e);
        }
    }

    private static Throwable invoke(IRemoteTransitionFinishedCallback callback,
            SurfaceControl.Transaction transaction) {
        try {
            IRemoteTransitionFinishedCallback.class
                    .getMethod("onTransitionFinished", SurfaceControl.Transaction.class)
                    .invoke(callback, transaction);
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return unwrap(e);
        }
    }

    private static Throwable invoke(IRemoteTransitionFinishedCallback callback,
            WindowContainerTransaction wct) {
        try {
            IRemoteTransitionFinishedCallback.class
                    .getMethod("onTransitionFinished", WindowContainerTransaction.class)
                    .invoke(callback, wct);
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return unwrap(e);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException
                && ((InvocationTargetException) throwable).getTargetException() != null) {
            return ((InvocationTargetException) throwable).getTargetException();
        }
        return throwable;
    }
}
