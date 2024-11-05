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

package com.android.wm.shell.common;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL;

import android.annotation.BinderThread;
import android.annotation.NonNull;
import android.os.RemoteException;
import android.util.Slog;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.WindowContainerTransaction;
import android.window.WindowContainerTransactionCallback;
import android.window.WindowOrganizer;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.transition.LegacyTransitions;

import java.util.ArrayList;

/**
 * Helper for serializing sync-transactions and corresponding callbacks.
 */
public final class SyncTransactionQueue {
    private static final boolean DEBUG = false;
    private static final String TAG = "SyncTransactionQueue";

    // Just a little longer than the sync-engine timeout of 5s
    private static final int REPLY_TIMEOUT = 5300;

    private final TransactionPool mTransactionPool;
    private final ShellExecutor mMainExecutor;

    // Sync Transactions currently don't support nesting or interleaving properly, so
    // queue up transactions to run them serially.
    private final ArrayList<SyncCallback> mQueue = new ArrayList<>();

    private SyncCallback mInFlight = null;
    private final ArrayList<TransactionRunnable> mRunnables = new ArrayList<>();

    private final Runnable mOnReplyTimeout = () -> {
        synchronized (mQueue) {
            if (mInFlight != null && mQueue.contains(mInFlight)) {
                Slog.w(TAG, "Sync Transaction timed-out: " + mInFlight.mWCT);
                mInFlight.onTransactionReady(mInFlight.mId, new SurfaceControl.Transaction());
            }
        }
    };

    public SyncTransactionQueue(TransactionPool pool, ShellExecutor mainExecutor) {
        mTransactionPool = pool;
        mMainExecutor = mainExecutor;
    }

    /**
     * Queues a sync transaction to be sent serially to WM.
     */
    public void queue(WindowContainerTransaction wct) {
        if (wct.isEmpty()) {
            if (DEBUG) Slog.d(TAG, "Skip queue due to transaction change is empty");
            return;
        }
        SyncCallback cb = new SyncCallback(wct);
        synchronized (mQueue) {
            if (DEBUG) Slog.d(TAG, "Queueing up " + wct);
            mQueue.add(cb);
            if (mQueue.size() == 1) {
                cb.send();
            }
        }
    }

    /**
     * Queues a legacy transition to be sent serially to WM
     */
    public void queue(LegacyTransitions.ILegacyTransition transition,
            @WindowManager.TransitionType int type, WindowContainerTransaction wct) {
        if (wct.isEmpty()) {
            if (DEBUG) Slog.d(TAG, "Skip queue due to transaction change is empty");
            return;
        }
        SyncCallback cb = new SyncCallback(transition, type, wct);
        synchronized (mQueue) {
            if (DEBUG) Slog.d(TAG, "Queueing up legacy transition " + wct);
            mQueue.add(cb);
            if (mQueue.size() == 1) {
                cb.send();
            }
        }
    }

    /**
     * Queues a sync transaction only if there are already sync transaction(s) queued or in flight.
     * Otherwise just returns without queueing.
     * @return {@code true} if queued, {@code false} if not.
     */
    public boolean queueIfWaiting(WindowContainerTransaction wct) {
        if (wct.isEmpty()) {
            if (DEBUG) Slog.d(TAG, "Skip queueIfWaiting due to transaction change is empty");
            return false;
        }
        synchronized (mQueue) {
            if (mQueue.isEmpty()) {
                if (DEBUG) Slog.d(TAG, "Nothing in queue, so skip queueing up " + wct);
                return false;
            }
            if (DEBUG) Slog.d(TAG, "Queue is non-empty, so queueing up " + wct);
            SyncCallback cb = new SyncCallback(wct);
            mQueue.add(cb);
            if (mQueue.size() == 1) {
                cb.send();
            }
        }
        return true;
    }

    /**
     * Runs a runnable in sync with sync transactions (ie. when the current in-flight transaction
     * returns. If there are no transactions in-flight, runnable executes immediately.
     */
    public void runInSync(TransactionRunnable runnable) {
        synchronized (mQueue) {
            if (DEBUG) Slog.d(TAG, "Run in sync. mInFlight=" + mInFlight);
            if (mInFlight != null) {
                mRunnables.add(runnable);
                return;
            }
        }
        SurfaceControl.Transaction t = mTransactionPool.acquire();
        runnable.runWithTransaction(t);
        t.apply();
        mTransactionPool.release(t);
    }

    // Synchronized on mQueue
    private void onTransactionReceived(@NonNull SurfaceControl.Transaction t) {
        if (DEBUG) Slog.d(TAG, "  Running " + mRunnables.size() + " sync runnables");
        final int n = mRunnables.size();
        for (int i = 0; i < n; ++i) {
            mRunnables.get(i).runWithTransaction(t);
        }
        // More runnables may have been added, so only remove the ones that ran.
        mRunnables.subList(0, n).clear();
    }

    /** Task to run with transaction. */
    public interface TransactionRunnable {
        /** Runs with transaction. */
        void runWithTransaction(SurfaceControl.Transaction t);
    }

    private class SyncCallback extends WindowContainerTransactionCallback {
        int mId = -1;
        final WindowContainerTransaction mWCT;
        final LegacyTransitions.LegacyTransition mLegacyTransition;

        SyncCallback(WindowContainerTransaction wct) {
            mWCT = wct;
            mLegacyTransition = null;
        }

        SyncCallback(LegacyTransitions.ILegacyTransition legacyTransition,
                @WindowManager.TransitionType int type, WindowContainerTransaction wct) {
            mWCT = wct;
            mLegacyTransition = new LegacyTransitions.LegacyTransition(type, legacyTransition);
        }

        // Must be sychronized on mQueue
        void send() {
            if (mInFlight == this) {
                // This was probably queued up and sent during a sync runnable of the last callback.
                // Don't queue it again.
                return;
            }
            if (mInFlight != null) {
                throw new IllegalStateException("Sync Transactions must be serialized. In Flight: "
                        + mInFlight.mId + " - " + mInFlight.mWCT);
            }
            mInFlight = this;
            if (DEBUG) Slog.d(TAG, "Sending sync transaction: " + mWCT);
            if (mLegacyTransition != null) {
                mId = new WindowOrganizer().startLegacyTransition(mLegacyTransition.getType(),
                        mLegacyTransition.getAdapter(), this, mWCT);
            } else {
                mId = new WindowOrganizer().applySyncTransaction(mWCT, this);
            }
            if (DEBUG) Slog.d(TAG, " Sent sync transaction. Got id=" + mId);
            mMainExecutor.executeDelayed(mOnReplyTimeout, REPLY_TIMEOUT);
        }

        @BinderThread
        @Override
        public void onTransactionReady(int id,
                @NonNull SurfaceControl.Transaction t) {
            ProtoLog.v(WM_SHELL, "SyncTransactionQueue.onTransactionReady(): syncId=%d", id);
            mMainExecutor.execute(() -> {
                synchronized (mQueue) {
                    if (mId != id) {
                        Slog.e(TAG, "Got an unexpected onTransactionReady. Expected "
                                + mId + " but got " + id);
                        return;
                    }
                    mInFlight = null;
                    mMainExecutor.removeCallbacks(mOnReplyTimeout);
                    if (DEBUG) Slog.d(TAG, "onTransactionReady id=" + mId);
                    mQueue.remove(this);
                    onTransactionReceived(t);
                    if (mLegacyTransition != null) {
                        try {
                            mLegacyTransition.getSyncCallback().onTransactionReady(mId, t);
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Error sending callback to legacy transition: " + mId, e);
                        }
                    } else {
                        ProtoLog.v(WM_SHELL,
                                "SyncTransactionQueue.onTransactionReady(): syncId=%d apply", id);
                        t.apply();
                        t.close();
                    }
                    if (!mQueue.isEmpty()) {
                        mQueue.get(0).send();
                    }
                }
            });
        }
    }
}
