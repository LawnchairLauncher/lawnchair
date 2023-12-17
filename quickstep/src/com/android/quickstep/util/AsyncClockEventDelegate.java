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
import android.os.Handler;
import android.provider.Settings;
import android.util.ArrayMap;
import android.widget.TextClock;

import androidx.annotation.WorkerThread;

import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.util.SettingsCache.OnChangeListener;
import com.android.launcher3.util.SimpleBroadcastReceiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension of {@link ClockEventDelegate} to support async event registration
 */
public class AsyncClockEventDelegate extends TextClock implements OnChangeListener {

    private final Context mContext;
    private final SimpleBroadcastReceiver mReceiver =
            new SimpleBroadcastReceiver(this::onClockEventReceived);

    private final ArrayMap<BroadcastReceiver, Handler> mTimeEventReceivers = new ArrayMap<>();
    private final List<ContentObserver> mFormatObservers = new ArrayList<>();
    private final Uri mFormatUri = Settings.System.getUriFor(Settings.System.TIME_12_24);

    private boolean mFormatRegistered = false;
    private boolean mDestroyed = false;

    public AsyncClockEventDelegate(Context context) {
        super(context);
        mContext = context;

        UI_HELPER_EXECUTOR.execute(() ->
                mReceiver.register(mContext, ACTION_TIME_CHANGED, ACTION_TIMEZONE_CHANGED));
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
     * Unregisters all system callbacks and destroys this delegate
     */
    public void onDestroy() {
        mDestroyed = true;
        SettingsCache.INSTANCE.get(mContext).unregister(mFormatUri, this);
        UI_HELPER_EXECUTOR.execute(() -> mReceiver.unregisterReceiverSafely(mContext));
    }
}
