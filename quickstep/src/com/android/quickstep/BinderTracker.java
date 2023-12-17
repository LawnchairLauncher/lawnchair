/**
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.os.IBinder.FLAG_ONEWAY;

import android.os.Binder;
import android.os.Binder.ProxyTransactListener;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.TraceHelper;

import java.util.LinkedList;
import java.util.Set;
import java.util.function.Consumer;

import kotlin.random.Random;

/**
 * A binder proxy transaction listener for tracking binder calls on main thread.
 */
public class BinderTracker {

    private static final String TAG = "BinderTracker";

    // Common IPCs that are ok to block the main thread.
    private static final Set<String> sAllowedFrameworkClasses = Set.of(
            "android.view.IWindowSession",
            "android.os.IPowerManager",
            "android.os.IServiceManager");

    /**
     * Starts tracking binder class and returns a {@link SafeCloseable} to end tracking
     */
    public static SafeCloseable startTracking(Consumer<BinderCallSite> callback) {
        TraceHelper current = TraceHelper.INSTANCE;

        TraceHelperExtension helper = new TraceHelperExtension(callback);
        TraceHelper.INSTANCE = helper;
        Binder.setProxyTransactListener(helper);

        return () -> {
            Binder.setProxyTransactListener(null);
            TraceHelper.INSTANCE = current;
        };
    }

    private static final LinkedList<String> mMainThreadTraceStack = new LinkedList<>();
    private static final LinkedList<String> mMainThreadIgnoreIpcStack = new LinkedList<>();

    private static class TraceHelperExtension extends TraceHelper implements ProxyTransactListener {

        private final Consumer<BinderCallSite> mUnexpectedTransactionCallback;

        TraceHelperExtension(Consumer<BinderCallSite> unexpectedTransactionCallback) {
            mUnexpectedTransactionCallback = unexpectedTransactionCallback;
        }

        @Override
        public void beginSection(String sectionName) {
            if (isMainThread()) {
                mMainThreadTraceStack.add(sectionName);
            }
            super.beginSection(sectionName);
        }

        @Override
        public SafeCloseable beginAsyncSection(String sectionName) {
            if (!isMainThread()) {
                return super.beginAsyncSection(sectionName);
            }

            mMainThreadTraceStack.add(sectionName);
            int cookie = Random.Default.nextInt();
            Trace.beginAsyncSection(sectionName, cookie);
            return () -> {
                Trace.endAsyncSection(sectionName, cookie);
                mMainThreadTraceStack.remove(sectionName);
            };
        }

        @Override
        public void endSection() {
            super.endSection();
            if (isMainThread()) {
                mMainThreadTraceStack.pollLast();
            }
        }

        @Override
        public SafeCloseable allowIpcs(String rpcName) {
            if (!isMainThread()) {
                return super.allowIpcs(rpcName);
            }

            mMainThreadTraceStack.add(rpcName);
            mMainThreadIgnoreIpcStack.add(rpcName);
            int cookie = Random.Default.nextInt();
            Trace.beginAsyncSection(rpcName, cookie);
            return () -> {
                Trace.endAsyncSection(rpcName, cookie);
                mMainThreadTraceStack.remove(rpcName);
                mMainThreadIgnoreIpcStack.remove(rpcName);
            };
        }

        @Override
        public Object onTransactStarted(IBinder binder, int transactionCode, int flags) {
            if (!isMainThread() || (flags & FLAG_ONEWAY) == FLAG_ONEWAY) {
                return null;
            }

            String ipcBypass = mMainThreadIgnoreIpcStack.peekLast();
            String descriptor;
            try {
                descriptor = binder.getInterfaceDescriptor();
                if (sAllowedFrameworkClasses.contains(descriptor)) {
                    return null;
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error getting IPC descriptor", e);
                descriptor = binder.getClass().getSimpleName();
            }

            if (ipcBypass == null) {
                mUnexpectedTransactionCallback.accept(new BinderCallSite(
                        mMainThreadTraceStack.peekLast(), descriptor, transactionCode));
            } else {
                Log.d(TAG, "MainThread-IPC " + descriptor + " ignored due to " + ipcBypass);
            }
            return null;
        }

        @Override
        public Object onTransactStarted(IBinder binder, int transactionCode) {
            // Do nothing
            return null;
        }

        @Override
        public void onTransactEnded(Object session) {
            // Do nothing
        }
    }

    private static boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    /**
     * Information about a binder call
     */
    public static class BinderCallSite {

        @Nullable
        public final String activeTrace;
        public final String descriptor;
        public final int transactionCode;

        BinderCallSite(String activeTrace, String descriptor, int transactionCode) {
            this.activeTrace = activeTrace;
            this.descriptor = descriptor;
            this.transactionCode = transactionCode;
        }
    }
}
