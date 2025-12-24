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
package com.android.launcher3.util;

import static android.content.Intent.ACTION_SCREEN_OFF;
import static android.content.Intent.ACTION_SCREEN_ON;
import static android.content.Intent.ACTION_USER_PRESENT;

import static com.android.launcher3.util.SimpleBroadcastReceiver.actionsFilter;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.concurrent.annotations.LightweightBackground;
import static com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority.UI;
import com.android.launcher3.concurrent.annotations.Ui;
import com.android.launcher3.util.LooperExecutor;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * Utility class for tracking if the screen is currently on or off
 */
@LauncherAppSingleton
public class ScreenOnTracker {

    public static final DaggerSingletonObject<ScreenOnTracker> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getScreenOnTracker);

    private final SimpleBroadcastReceiver mReceiver;
    private final CopyOnWriteArrayList<ScreenOnListener> mListeners = new CopyOnWriteArrayList<>();

    private boolean mIsScreenOn;

    @Inject
    ScreenOnTracker(@ApplicationContext Context context, DaggerSingletonTracker tracker,
            @Ui LooperExecutor uiExecutor,
            @LightweightBackground(priority = UI) LooperExecutor lightweightBackgroundExecutor) {
        // Assume that the screen is on to begin with
        mReceiver = new SimpleBroadcastReceiver(
                context, lightweightBackgroundExecutor, uiExecutor, this::onReceive);
        init(tracker);
    }

    @VisibleForTesting
    ScreenOnTracker(SimpleBroadcastReceiver receiver, DaggerSingletonTracker tracker) {
        mReceiver = receiver;
        init(tracker);
    }

    private void init(DaggerSingletonTracker tracker) {
        mIsScreenOn = true;
        mReceiver.register(
                actionsFilter(ACTION_SCREEN_ON, ACTION_SCREEN_OFF, ACTION_USER_PRESENT),
                0, null, null); // Add all arguments to allow argument matcher
        tracker.addCloseable(mReceiver);
    }

    @VisibleForTesting
    void onReceive(Intent intent) {
        String action = intent.getAction();
        if (ACTION_SCREEN_ON.equals(action)) {
            mIsScreenOn = true;
            dispatchScreenOnChanged();
        } else if (ACTION_SCREEN_OFF.equals(action)) {
            mIsScreenOn = false;
            dispatchScreenOnChanged();
        } else if (ACTION_USER_PRESENT.equals(action)) {
            mListeners.forEach(ScreenOnListener::onUserPresent);
        }
    }

    private void dispatchScreenOnChanged() {
        mListeners.forEach(l -> l.onScreenOnChanged(mIsScreenOn));
    }

    /** Returns if the screen is on or not */
    public boolean isScreenOn() {
        return mIsScreenOn;
    }

    /** Adds a listener for screen on changes */
    public void addListener(ScreenOnListener listener) {
        mListeners.add(listener);
    }

    /** Removes a previously added listener */
    public void removeListener(ScreenOnListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Interface to listen for screen on changes
     */
    public interface ScreenOnListener {

        /**
         * Called when the screen turns on/off
         */
        void onScreenOnChanged(boolean isOn);

        /**
         * Called when the keyguard goes away
         */
        default void onUserPresent() { }
    }
}
