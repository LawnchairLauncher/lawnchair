/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.states;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.model.BgDataModel.Callbacks;

import java.lang.ref.WeakReference;

/**
 * Utility class to sending state handling logic to Launcher from within the same process.
 *
 * Extending {@link Binder} ensures that the platform maintains a single instance of each object
 * which allows this object to safely navigate the system process.
 */
public abstract class InternalStateHandler extends Binder {

    public static final String EXTRA_STATE_HANDLER = "launcher.state_handler";

    private static final Scheduler sScheduler = new Scheduler();

    /**
     * Initializes the handler when the launcher is ready.
     * @return true if the handler wants to stay alive.
     */
    protected abstract boolean init(Launcher launcher, boolean alreadyOnHome);

    public final Intent addToIntent(Intent intent) {
        Bundle extras = new Bundle();
        extras.putBinder(EXTRA_STATE_HANDLER, this);
        intent.putExtras(extras);
        return intent;
    }

    public final void initWhenReady() {
        sScheduler.schedule(this);
    }

    public boolean clearReference() {
        return sScheduler.clearReference(this);
    }

    public static boolean hasPending() {
        return sScheduler.hasPending();
    }

    public static boolean handleCreate(Launcher launcher, Intent intent) {
        return handleIntent(launcher, intent, false, false);
    }

    public static boolean handleNewIntent(Launcher launcher, Intent intent, boolean alreadyOnHome) {
        return handleIntent(launcher, intent, alreadyOnHome, true);
    }

    private static boolean handleIntent(
            Launcher launcher, Intent intent, boolean alreadyOnHome, boolean explicitIntent) {
        boolean result = false;
        if (intent != null && intent.getExtras() != null) {
            IBinder stateBinder = intent.getExtras().getBinder(EXTRA_STATE_HANDLER);
            if (stateBinder instanceof InternalStateHandler) {
                InternalStateHandler handler = (InternalStateHandler) stateBinder;
                if (!handler.init(launcher, alreadyOnHome)) {
                    intent.getExtras().remove(EXTRA_STATE_HANDLER);
                }
                result = true;
            }
        }
        if (!result && !explicitIntent) {
            result = sScheduler.initIfPending(launcher, alreadyOnHome);
        }
        return result;
    }

    private static class Scheduler implements Runnable {

        private WeakReference<InternalStateHandler> mPendingHandler = new WeakReference<>(null);

        public void schedule(InternalStateHandler handler) {
            synchronized (this) {
                mPendingHandler = new WeakReference<>(handler);
            }
            MAIN_EXECUTOR.execute(this);
        }

        @Override
        public void run() {
            LauncherAppState app = LauncherAppState.getInstanceNoCreate();
            if (app == null) {
                return;
            }
            Callbacks cb = app.getModel().getCallback();
            if (!(cb instanceof Launcher)) {
                return;
            }
            Launcher launcher = (Launcher) cb;
            initIfPending(launcher, launcher.isStarted());
        }

        public boolean initIfPending(Launcher launcher, boolean alreadyOnHome) {
            InternalStateHandler pendingHandler = mPendingHandler.get();
            if (pendingHandler != null) {
                if (!pendingHandler.init(launcher, alreadyOnHome)) {
                    clearReference(pendingHandler);
                }
                return true;
            }
            return false;
        }

        public boolean clearReference(InternalStateHandler handler) {
            synchronized (this) {
                if (mPendingHandler.get() == handler) {
                    mPendingHandler.clear();
                    return true;
                }
                return false;
            }
        }

        public boolean hasPending() {
            return mPendingHandler.get() != null;
        }
    }
}