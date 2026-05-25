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

    /**
     * Initializes the AsyncClockEventDelegate, registers for system time and timezone broadcasts,
     * and registers this instance with the provided singleton tracker for automatic closing.
     *
     * <p>Constructs the internal broadcast receiver that will forward time/timezone events to
     * this delegate and stores the provided SettingsCache for later format-change registration.
     *
     * @param context the application context used for registering receivers and event dispatch
     * @param tracker a lifecycle tracker used to ensure this instance is closed when the app shuts down
     * @param settingsCache settings cache used to observe 12/24-hour format changes
     */
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
     * Lazily creates a delegate that forwards clock event registration and callbacks to this singleton.
     *
     * <p>Must be called only on Android 14 (Upside Down Cake) or newer; loading the nested {@link Delegate}
     * on older platforms will fail because its superclass is unavailable.
     *
     * @return the {@link ClockEventDelegate} instance that forwards to this singleton
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public ClockEventDelegate asClockEventDelegate() {
        if (mDelegate == null) {
            mDelegate = new Delegate(this);
        }
        return mDelegate;
    }

    /**
     * Registers a BroadcastReceiver to be notified when the system time or timezone changes.
     *
     * The receiver will be invoked on the provided Handler; if {@code handler} is {@code null} a new default
     * Handler will be created and used.
     *
     * @param receiver the BroadcastReceiver to notify on time/timezone change events
     * @param handler  the Handler on which to invoke the receiver, or {@code null} to use a newly created default Handler
     */
    void registerTimeChangeReceiver(BroadcastReceiver receiver, Handler handler) {
        synchronized (mTimeEventReceivers) {
            mTimeEventReceivers.put(receiver, handler == null ? new Handler() : handler);
        }
    }

    /**
     * Stops delivering time and timezone change events to the given receiver.
     *
     * @param receiver the BroadcastReceiver previously registered for time/timezone events;
     *                 if the receiver is not registered this method has no effect
     */
    void unregisterTimeChangeReceiver(BroadcastReceiver receiver) {
        synchronized (mTimeEventReceivers) {
            mTimeEventReceivers.remove(receiver);
        }
    }

    /**
     * Registers a ContentObserver to be notified when the system 12/24-hour time format changes.
     *
     * If this delegate has been destroyed, the observer is not registered. The method ensures
     * the underlying settings change source for TIME_12_24 is registered once before adding
     * the observer.
     *
     * @param observer the ContentObserver to notify on format changes
     * @param userHandle an integer user identifier; currently accepted but ignored by this implementation
     */
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

    /**
     * Stops dispatching time format change notifications to the given ContentObserver.
     *
     * Removes the observer from the internal list of format-change observers. Safe to call
     * if the observer is not currently registered; this method is thread-safe.
     *
     * @param observer the ContentObserver to unregister
     */
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

    /**
     * Releases internal resources and stops future clock and format event delivery.
     *
     * Sets the delegate to a destroyed state, unregisters the time-format listener from the settings cache, and unregisters the internal broadcast receiver.
     */
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

        /**
         * Creates a ClockEventDelegate that forwards delegate calls to the given owner.
         *
         * @param owner the AsyncClockEventDelegate that will receive forwarded registrations and events
         */
        Delegate(AsyncClockEventDelegate owner) {
            super(owner.mContext);
            mOwner = owner;
        }

        /**
         * Registers a BroadcastReceiver to receive time and timezone change events.
         *
         * @param receiver the receiver that will be notified when time or timezone changes occur
         * @param handler  the handler on which to invoke the receiver; if null, the receiver's
         *                 onReceive may be invoked on a default handler
         */
        @Override
        public void registerTimeChangeReceiver(BroadcastReceiver receiver, Handler handler) {
            mOwner.registerTimeChangeReceiver(receiver, handler);
        }

        /**
         * Stops delivering time and timezone change events to the given receiver.
         *
         * @param receiver the BroadcastReceiver previously registered to receive time change events
         */
        @Override
        public void unregisterTimeChangeReceiver(BroadcastReceiver receiver) {
            mOwner.unregisterTimeChangeReceiver(receiver);
        }

        /**
         * Registers a ContentObserver to be notified when the system 12/24-hour time format changes.
         *
         * <p>The provided `observer` will receive change notifications for the
         * Settings.System.TIME_12_24 setting.</p>
         *
         * @param observer the observer to notify when the time format changes
         * @param userHandle ignored; kept for API compatibility
         */
        @Override
        public void registerFormatChangeObserver(ContentObserver observer, int userHandle) {
            mOwner.registerFormatChangeObserver(observer, userHandle);
        }

        /**
         * Unregisters a ContentObserver previously registered for time format (12/24-hour) changes.
         *
         * @param observer the ContentObserver to unregister; if the observer was not registered this is a no-op
         */
        @Override
        public void unregisterFormatChangeObserver(ContentObserver observer) {
            mOwner.unregisterFormatChangeObserver(observer);
        }
    }
}
