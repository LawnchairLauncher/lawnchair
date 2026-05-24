/*
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
package com.android.quickstep.util;

import static android.content.Intent.ACTION_TIMEZONE_CHANGED;
import static android.content.Intent.ACTION_TIME_CHANGED;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.ArrayMap;
import android.widget.TextClock.ClockEventDelegate;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.util.DaggerSingletonObject;
import com.android.launcher3.util.DaggerSingletonTracker;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SettingsCache.OnChangeListener;
import com.android.launcher3.util.SimpleBroadcastReceiver;
import com.android.quickstep.dagger.QuickstepBaseAppComponent;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Holder for async time/format event registration that can back a
 * {@link ClockEventDelegate} on Android 14+.
 *
 * <p>This class intentionally does <b>not</b> extend {@link ClockEventDelegate} directly because
 * that class only exists on Android 14 ({@link Build.VERSION_CODES#UPSIDE_DOWN_CAKE}); doing so
 * would cause {@code NoClassDefFoundError} when ART tries to resolve the superclass on older
 * platforms (the Dagger-generated factory references this class during component build). Instead
 * the actual {@code ClockEventDelegate} subclass lives in the nested {@link Delegate} class which
 * is only loaded when {@link #asClockEventDelegate()} is called from API 34+ code paths.
 */
@LauncherAppSingleton
public class AsyncClockEventDelegate implements OnChangeListener, SafeCloseable {

    public static final DaggerSingletonObject<AsyncClockEventDelegate> INSTANCE =
            new DaggerSingletonObject<>(QuickstepBaseAppComponent::getAsyncClockEventDelegate);

    private final Context mContext;
    private final SettingsCache mSettingsCache;
    private final SimpleBroadcastReceiver mReceiver;

    private final ArrayMap<BroadcastReceiver, Handler> mTimeEventReceivers = new ArrayMap<>();
    private final List<ContentObserver> mFormatObservers = new ArrayList<>();
    private final Uri mFormatUri = Settings.System.getUriFor(Settings.System.TIME_12_24);

    private boolean mFormatRegistered = false;
    private boolean mDestroyed = false;

    @Nullable
    private ClockEventDelegate mDelegate;

    @Inject
    AsyncClockEventDelegate(@ApplicationContext Context context,
            DaggerSingletonTracker tracker,
            SettingsCache settingsCache) {
        mContext = context;
        mSettingsCache = settingsCache;
        mReceiver = new SimpleBroadcastReceiver(
                context, UI_HELPER_EXECUTOR, this::onClockEventReceived);
        mReceiver.register(ACTION_TIME_CHANGED, ACTION_TIMEZONE_CHANGED);
        tracker.addCloseable(this);
    }

    /**
     * Lazily creates and returns a {@link ClockEventDelegate} that forwards to this singleton.
     *
     * <p>Must only be called on Android 14+; on older platforms loading the nested {@link Delegate}
     * class would fail because its superclass does not exist.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public ClockEventDelegate asClockEventDelegate() {
        if (mDelegate == null) {
            mDelegate = new Delegate(this);
        }
        return mDelegate;
    }

    void registerTimeChangeReceiver(BroadcastReceiver receiver, Handler handler) {
        synchronized (mTimeEventReceivers) {
            mTimeEventReceivers.put(receiver, handler == null ? new Handler() : handler);
        }
    }

    void unregisterTimeChangeReceiver(BroadcastReceiver receiver) {
        synchronized (mTimeEventReceivers) {
            mTimeEventReceivers.remove(receiver);
        }
    }

    void registerFormatChangeObserver(ContentObserver observer, int userHandle) {
        if (mDestroyed) {
            return;
        }
        synchronized (mFormatObservers) {
            if (!mFormatRegistered && !mDestroyed) {
                mSettingsCache.register(mFormatUri, this);
                mFormatRegistered = true;
            }
            mFormatObservers.add(observer);
        }
    }

    void unregisterFormatChangeObserver(ContentObserver observer) {
        synchronized (mFormatObservers) {
            mFormatObservers.remove(observer);
        }
    }

    @Override
    public void onSettingsChanged(boolean isEnabled) {
        if (mDestroyed) {
            return;
        }
        synchronized (mFormatObservers) {
            mFormatObservers.forEach(o -> o.dispatchChange(false, mFormatUri));
        }
    }
    @WorkerThread
    private void onClockEventReceived(Intent intent) {
        if (mDestroyed) {
            return;
        }
        synchronized (mReceiver) {
            mTimeEventReceivers.forEach((r, h) -> h.post(() -> r.onReceive(mContext, intent)));
        }
    }

    @Override
    public void close() {
        mDestroyed = true;
        mSettingsCache.unregister(mFormatUri, this);
        mReceiver.unregisterReceiverSafely();
    }

    /**
     * {@link ClockEventDelegate} subclass that forwards every method to an
     * {@link AsyncClockEventDelegate}. Lives in its own {@code .class} file so it is only loaded
     * (and verified by ART) on Android 14+ where {@link ClockEventDelegate} actually exists.
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static final class Delegate extends ClockEventDelegate {

        private final AsyncClockEventDelegate mOwner;

        Delegate(AsyncClockEventDelegate owner) {
            super(owner.mContext);
            mOwner = owner;
        }

        @Override
        public void registerTimeChangeReceiver(BroadcastReceiver receiver, Handler handler) {
            mOwner.registerTimeChangeReceiver(receiver, handler);
        }

        @Override
        public void unregisterTimeChangeReceiver(BroadcastReceiver receiver) {
            mOwner.unregisterTimeChangeReceiver(receiver);
        }

        @Override
        public void registerFormatChangeObserver(ContentObserver observer, int userHandle) {
            mOwner.registerFormatChangeObserver(observer, userHandle);
        }

        @Override
        public void unregisterFormatChangeObserver(ContentObserver observer) {
            mOwner.unregisterFormatChangeObserver(observer);
        }
    }
}
