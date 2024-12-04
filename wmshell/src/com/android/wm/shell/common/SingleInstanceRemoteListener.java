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

package com.android.wm.shell.common;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.util.Slog;

import androidx.annotation.BinderThread;

import java.util.function.Consumer;

/**
 * Manages the lifecycle of a single instance of a remote listener, including the clean up if the
 * remote process dies.  All calls on this class should happen on the main shell thread.
 *
 * Any external interface using this listener should also unregister the listener when it is
 * invalidated, otherwise it may leak binder death recipients.
 *
 * @param <C> The controller (must be RemoteCallable)
 * @param <L> The remote listener interface type
 */
public class SingleInstanceRemoteListener<C extends RemoteCallable, L extends IInterface> {
    private static final String TAG = SingleInstanceRemoteListener.class.getSimpleName();

    /**
     * Simple callable interface that throws a remote exception.
     */
    public interface RemoteCall<L> {
        void accept(L l) throws RemoteException;
    }

    private final C mCallableController;
    private final Consumer<C> mOnRegisterCallback;
    private final Consumer<C> mOnUnregisterCallback;

    L mListener;

    private final IBinder.DeathRecipient mListenerDeathRecipient =
            new IBinder.DeathRecipient() {
                @Override
                @BinderThread
                public void binderDied() {
                    final C callableController = mCallableController;
                    mCallableController.getRemoteCallExecutor().execute(() -> {
                        mListener = null;
                        mOnUnregisterCallback.accept(callableController);
                    });
                }
            };

    /**
     * @param onRegisterCallback Callback when register() is called (same thread)
     * @param onUnregisterCallback Callback when unregister() is called (same thread as unregister()
     *                             or the callableController.getRemoteCallbackExecutor() thread)
     */
    public SingleInstanceRemoteListener(C callableController,
            Consumer<C> onRegisterCallback,
            Consumer<C> onUnregisterCallback) {
        mCallableController = callableController;
        mOnRegisterCallback = onRegisterCallback;
        mOnUnregisterCallback = onUnregisterCallback;
    }

    /**
     * Registers this listener, storing a reference to it and calls the provided method in the
     * constructor.
     */
    public void register(L listener) {
        if (mListener != null) {
            mListener.asBinder().unlinkToDeath(mListenerDeathRecipient, 0 /* flags */);
        }
        if (listener != null) {
            try {
                listener.asBinder().linkToDeath(mListenerDeathRecipient, 0 /* flags */);
            } catch (RemoteException e) {
                Slog.e(TAG, "Failed to link to death");
                return;
            }
        }
        mListener = listener;
        mOnRegisterCallback.accept(mCallableController);
    }

    /**
     * Unregisters this listener, removing all references to it and calls the provided method in the
     * constructor.
     */
    public void unregister() {
        if (mListener != null) {
            mListener.asBinder().unlinkToDeath(mListenerDeathRecipient, 0 /* flags */);
        }
        mListener = null;
        mOnUnregisterCallback.accept(mCallableController);
    }

    /**
     * Safely wraps a call to the remote listener.
     */
    public void call(RemoteCall<L> handler) {
        if (mListener == null) {
            Slog.e(TAG, "Failed remote call on null listener");
            return;
        }
        try {
            handler.accept(mListener);
        } catch (RemoteException e) {
            Slog.e(TAG, "Failed remote call", e);
        }
    }
}