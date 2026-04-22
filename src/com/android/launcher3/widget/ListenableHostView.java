/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.RemoteViews;

import com.android.launcher3.util.SafeCloseable;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class ListenableHostView extends LauncherAppWidgetHostView {

    private Set<Runnable> mUpdateListeners = Collections.EMPTY_SET;

    ListenableHostView(Context context) {
        super(context);
    }

    @Override
    public void updateAppWidget(RemoteViews remoteViews) {
        super.updateAppWidget(remoteViews);
        mUpdateListeners.forEach(Runnable::run);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(LauncherAppWidgetHostView.class.getName());
    }

    /**
     * Adds a callback to be run everytime the provided app widget updates.
     * @return a closable to remove this callback
     */
    public SafeCloseable addUpdateListener(Runnable callback) {
        if (mUpdateListeners == Collections.EMPTY_SET) {
            mUpdateListeners = Collections.newSetFromMap(new WeakHashMap<>());
        }
        mUpdateListeners.add(callback);
        return () -> mUpdateListeners.remove(callback);
    }
}
