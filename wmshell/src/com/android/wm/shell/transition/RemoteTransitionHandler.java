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

package com.android.wm.shell.transition;

import static com.android.systemui.shared.Flags.returnAnimationFrameworkLibrary;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Pair;
import android.util.Slog;
import android.view.SurfaceControl;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.RemoteTransition;
import android.window.TransitionFilter;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerTransaction;

import androidx.annotation.BinderThread;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Handler that deals with RemoteTransitions. It will only request to handle a transition
 * if the request includes a specific remote.
 */
public class RemoteTransitionHandler implements Transitions.TransitionHandler {
    private static final String TAG = "RemoteTransitionHandler";

    private final ShellExecutor mMainExecutor;

    /** Includes remotes explicitly requested by, eg, ActivityOptions */
    private final ArrayMap<IBinder, RemoteTransition> mRequestedRemotes = new ArrayMap<>();

    /** Ordered by specificity. Last filters will be checked first */
    private final ArrayList<Pair<TransitionFilter, RemoteTransition>> mFilters =
            new ArrayList<>();
    private final ArrayList<Pair<TransitionFilter, RemoteTransition>> mTakeoverFilters =
            new ArrayList<>();

    private final ArrayMap<IBinder, RemoteDeathHandler> mDeathHandlers = new ArrayMap<>();

    RemoteTransitionHandler(@NonNull ShellExecutor mainExecutor) {
        mMainExecutor = mainExecutor;
    }

    void addFiltered(TransitionFilter filter, RemoteTransition remote) {
        handleDeath(remote.asBinder(), null /* finishCallback */);
        mFilters.add(new Pair<>(filter, remote));
    }

    void addFilteredForTakeover(TransitionFilter filter, RemoteTransition remote) {
        handleDeath(remote.asBinder(), null /* finishCallback */);
        mTakeoverFilters.add(new Pair<>(filter, remote));
    }

    void removeFiltered(RemoteTransition remote) {
        boolean removed = false;
        for (ArrayList<Pair<TransitionFilter, RemoteTransition>> filters
                : Arrays.asList(mFilters, mTakeoverFilters)) {
            for (int i = filters.size() - 1; i >= 0; --i) {
                if (filters.get(i).second.asBinder().equals(remote.asBinder())) {
                    filters.remove(i);
                    removed = true;
                }
            }
        }

        if (removed) {
            unhandleDeath(remote.asBinder(), null /* finishCallback */);
        }
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {
        RemoteTransition remoteTransition = mRequestedRemotes.remove(transition);
        if (remoteTransition == null) {
            return;
        }

        try {
            remoteTransition.getRemoteTransition().onTransitionConsumed(transition, aborted);
        } catch (RemoteException e) {
            Log.e(TAG, "Error delegating onTransitionConsumed()", e);
        }
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (!Transitions.SHELL_TRANSITIONS_ROTATION && TransitionUtil.hasDisplayChange(info)) {
            // Note that if the remote doesn't have permission ACCESS_SURFACE_FLINGER, some
            // operations of the start transaction may be ignored.
            mRequestedRemotes.remove(transition);
            return false;
        }
        RemoteTransition pendingRemote = mRequestedRemotes.get(transition);
        if (pendingRemote == null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "Transition doesn't have "
                    + "explicit remote, search filters for match for %s", info);
            // If no explicit remote, search filters until one matches
            for (int i = mFilters.size() - 1; i >= 0; --i) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Checking filter %s",
                        mFilters.get(i));
                if (mFilters.get(i).first.matches(info)) {
                    Slog.d(TAG, "Found filter" + mFilters.get(i));
                    pendingRemote = mFilters.get(i).second;
                    // Add to requested list so that it can be found for merge requests.
                    mRequestedRemotes.put(transition, pendingRemote);
                    break;
                }
            }
        }
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, " Delegate animation for (#%d) to %s",
                info.getDebugId(), pendingRemote);

        if (pendingRemote == null) return false;

        final RemoteTransition remote = pendingRemote;
        IRemoteTransitionFinishedCallback cb = new IRemoteTransitionFinishedCallback.Stub() {
            @Override
            public void onTransitionFinished(WindowContainerTransaction wct,
                    SurfaceControl.Transaction sct) {
                unhandleDeath(remote.asBinder(), finishCallback);
                if (sct != null) {
                    finishTransaction.merge(sct);
                }
                mMainExecutor.execute(() -> {
                    mRequestedRemotes.remove(transition);
                    finishCallback.onTransitionFinished(wct);
                });
            }
        };
        // If the remote is actually in the same process, then make a copy of parameters since
        // remote impls assume that they have to clean-up native references.
        final SurfaceControl.Transaction remoteStartT =
                copyIfLocal(startTransaction, remote.getRemoteTransition());
        final TransitionInfo remoteInfo =
                remoteStartT == startTransaction ? info : info.localRemoteCopy();
        try {
            handleDeath(remote.asBinder(), finishCallback);
            remote.getRemoteTransition().startAnimation(transition, remoteInfo, remoteStartT, cb);
            // assume that remote will apply the start transaction.
            startTransaction.clear();
            Transitions.setRunningRemoteTransitionDelegate(remote.getAppThread());
        } catch (RemoteException e) {
            Log.e(Transitions.TAG, "Error running remote transition.", e);
            if (remoteStartT != startTransaction) {
                remoteStartT.close();
            }
            startTransaction.apply();
            unhandleDeath(remote.asBinder(), finishCallback);
            mRequestedRemotes.remove(transition);
            mMainExecutor.execute(() -> finishCallback.onTransitionFinished(null /* wct */));
        }
        return true;
    }

    static SurfaceControl.Transaction copyIfLocal(SurfaceControl.Transaction t,
            IRemoteTransition remote) {
        // We care more about parceling than local (though they should be the same); so, use
        // queryLocalInterface since that's what Binder uses to decide if it needs to parcel.
        if (remote.asBinder().queryLocalInterface(IRemoteTransition.DESCRIPTOR) == null) {
            // No local interface, so binder itself will parcel and thus we don't need to.
            return t;
        }
        // Binder won't be parceling; however, the remotes assume they have their own native
        // objects (and don't know if caller is local or not), so we need to make a COPY here so
        // that the remote can clean it up without clearing the original transaction.
        // Since there's no direct `copy` for Transaction, we have to parcel/unparcel instead.
        final Parcel p = Parcel.obtain();
        try {
            t.writeToParcel(p, 0);
            p.setDataPosition(0);
            return SurfaceControl.Transaction.CREATOR.createFromParcel(p);
        } finally {
            p.recycle();
        }
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final RemoteTransition remoteTransition = mRequestedRemotes.get(mergeTarget);
        if (remoteTransition == null) return;

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "   Merge into remote: %s",
                remoteTransition);

        final IRemoteTransition remote = remoteTransition.getRemoteTransition();
        if (remote == null) return;

        IRemoteTransitionFinishedCallback cb = new IRemoteTransitionFinishedCallback.Stub() {
            @Override
            public void onTransitionFinished(WindowContainerTransaction wct,
                    SurfaceControl.Transaction sct) {
                // We have merged, since we sent the transaction over binder, the one in this
                // process won't be cleared if the remote applied it. We don't actually know if the
                // remote applied the transaction, but applying twice will break surfaceflinger
                // so just assume the worst-case and clear the local transaction.
                t.clear();
                mMainExecutor.execute(() -> {
                    if (!mRequestedRemotes.containsKey(mergeTarget)) {
                        Log.e(TAG, "Merged transition finished after it's mergeTarget (the "
                                + "transition it was supposed to merge into). This usually means "
                                + "that the mergeTarget's RemoteTransition impl erroneously "
                                + "accepted/ran the merge request after finishing the mergeTarget");
                    }
                    finishCallback.onTransitionFinished(wct);
                });
            }
        };
        try {
            // If the remote is actually in the same process, then make a copy of parameters since
            // remote impls assume that they have to clean-up native references.
            final SurfaceControl.Transaction remoteT = copyIfLocal(t, remote);
            final TransitionInfo remoteInfo = remoteT == t ? info : info.localRemoteCopy();
            remote.mergeAnimation(transition, remoteInfo, remoteT, mergeTarget, cb);
        } catch (RemoteException e) {
            Log.e(Transitions.TAG, "Error attempting to merge remote transition.", e);
        }
    }

    @Nullable
    @Override
    public Transitions.TransitionHandler getHandlerForTakeover(
            @NonNull IBinder transition, @NonNull TransitionInfo info) {
        if (!returnAnimationFrameworkLibrary()) {
            return null;
        }

        for (Pair<TransitionFilter, RemoteTransition> registered : mTakeoverFilters) {
            if (registered.first.matches(info)) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                        "Found matching remote to takeover (#%d)", info.getDebugId());

                OneShotRemoteHandler oneShot =
                        new OneShotRemoteHandler(mMainExecutor, registered.second);
                oneShot.setTransition(transition);
                return oneShot;
            }
        }

        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                "No matching remote found to takeover (#%d)", info.getDebugId());
        return null;
    }

    @Override
    public boolean takeOverAnimation(
            @NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction transaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback,
            @NonNull WindowAnimationState[] states) {
        Transitions.TransitionHandler handler = getHandlerForTakeover(transition, info);
        if (handler == null) {
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_RECENTS_TRANSITION,
                    "Take over request failed: no matching remote for (#%d)", info.getDebugId());
            return false;
        }
        ((OneShotRemoteHandler) handler).setTransition(transition);
        return handler.takeOverAnimation(transition, info, transaction, finishCallback, states);
    }

    @Override
    @Nullable
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @Nullable TransitionRequestInfo request) {
        RemoteTransition remote = request.getRemoteTransition();
        if (remote == null) return null;
        mRequestedRemotes.put(transition, remote);
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_TRANSITIONS, "RemoteTransition directly requested"
                + " for (#%d) %s: %s", request.getDebugId(), transition, remote);
        return new WindowContainerTransaction();
    }

    private void handleDeath(@NonNull IBinder remote,
            @Nullable Transitions.TransitionFinishCallback finishCallback) {
        synchronized (mDeathHandlers) {
            RemoteDeathHandler deathHandler = mDeathHandlers.get(remote);
            if (deathHandler == null) {
                deathHandler = new RemoteDeathHandler(remote);
                try {
                    remote.linkToDeath(deathHandler, 0 /* flags */);
                } catch (RemoteException e) {
                    Slog.e(TAG, "Failed to link to death");
                    return;
                }
                mDeathHandlers.put(remote, deathHandler);
            }
            deathHandler.addUser(finishCallback);
        }
    }

    private void unhandleDeath(@NonNull IBinder remote,
            @Nullable Transitions.TransitionFinishCallback finishCallback) {
        synchronized (mDeathHandlers) {
            RemoteDeathHandler deathHandler = mDeathHandlers.get(remote);
            if (deathHandler == null) return;
            deathHandler.removeUser(finishCallback);
            if (deathHandler.getUserCount() == 0) {
                if (!deathHandler.mPendingFinishCallbacks.isEmpty()) {
                    throw new IllegalStateException("Unhandling death for binder that still has"
                            + " pending finishCallback(s).");
                }
                remote.unlinkToDeath(deathHandler, 0 /* flags */);
                mDeathHandlers.remove(remote);
            }
        }
    }

    void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";

        pw.println(prefix + "Registered Remotes:");
        if (mFilters.isEmpty()) {
            pw.println(innerPrefix + "none");
        } else {
            for (Pair<TransitionFilter, RemoteTransition> entry : mFilters) {
                dumpRemote(pw, innerPrefix, entry.second);
            }
        }

        pw.println(prefix + "Registered Takeover Remotes:");
        if (mTakeoverFilters.isEmpty()) {
            pw.println(innerPrefix + "none");
        } else {
            for (Pair<TransitionFilter, RemoteTransition> entry : mTakeoverFilters) {
                dumpRemote(pw, innerPrefix, entry.second);
            }
        }
    }

    private void dumpRemote(@NonNull PrintWriter pw, String prefix, RemoteTransition remote) {
        pw.print(prefix);
        pw.print(remote.getDebugName());
        pw.println(" (" + Integer.toHexString(System.identityHashCode(remote)) + ")");
    }

    /** NOTE: binder deaths can alter the filter order */
    private class RemoteDeathHandler implements IBinder.DeathRecipient {
        private final IBinder mRemote;
        private final ArrayList<Transitions.TransitionFinishCallback> mPendingFinishCallbacks =
                new ArrayList<>();
        private int mUsers = 0;

        RemoteDeathHandler(IBinder remote) {
            mRemote = remote;
        }

        void addUser(@Nullable Transitions.TransitionFinishCallback finishCallback) {
            if (finishCallback != null) {
                mPendingFinishCallbacks.add(finishCallback);
            }
            ++mUsers;
        }

        void removeUser(@Nullable Transitions.TransitionFinishCallback finishCallback) {
            if (finishCallback != null) {
                mPendingFinishCallbacks.remove(finishCallback);
            }
            --mUsers;
        }

        int getUserCount() {
            return mUsers;
        }

        @Override
        @BinderThread
        public void binderDied() {
            mMainExecutor.execute(() -> {
                for (int i = mFilters.size() - 1; i >= 0; --i) {
                    if (mRemote.equals(mFilters.get(i).second.asBinder())) {
                        mFilters.remove(i);
                    }
                }
                for (int i = mRequestedRemotes.size() - 1; i >= 0; --i) {
                    if (mRemote.equals(mRequestedRemotes.valueAt(i).asBinder())) {
                        mRequestedRemotes.removeAt(i);
                    }
                }
                for (int i = mPendingFinishCallbacks.size() - 1; i >= 0; --i) {
                    mPendingFinishCallbacks.get(i).onTransitionFinished(null /* wct */);
                }
                mPendingFinishCallbacks.clear();
            });
        }
    }
}
