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
package com.android.quickstep.util;

import androidx.annotation.NonNull;

import com.android.systemui.unfold.updates.screen.ScreenStatusProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen status provider implementation that exposes methods to provide screen
 * status updates to listeners. It is used to receive screen turned on event from
 * SystemUI to Launcher.
 */
public class ProxyScreenStatusProvider implements ScreenStatusProvider {

    public static final ProxyScreenStatusProvider INSTANCE = new ProxyScreenStatusProvider();
    private final List<ScreenListener> mListeners = new ArrayList<>();

    /**
     * Called when the screen is on and ready (windows are drawn and screen blocker is removed)
     */
    public void onScreenTurnedOn() {
        mListeners.forEach(ScreenListener::onScreenTurnedOn);
    }

    /** Called when the screen is starting to turn on. */
    public void onScreenTurningOn() {
        mListeners.forEach(ScreenListener::onScreenTurningOn);
    }

    /** Called when the screen is starting to turn off. */
    public void onScreenTurningOff() {
        mListeners.forEach(ScreenListener::onScreenTurningOff);
    }

    @Override
    public void addCallback(@NonNull ScreenListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void removeCallback(@NonNull ScreenListener listener) {
        mListeners.remove(listener);
    }
}
