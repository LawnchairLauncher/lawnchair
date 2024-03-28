/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;

import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.List;

// TODO(b/218912746): Share more behavior to avoid all apps context depending directly on taskbar.
/** Base for common behavior between taskbar window contexts. */
public abstract class BaseTaskbarContext extends ContextThemeWrapper implements ActivityContext {

    protected final LayoutInflater mLayoutInflater;
    private final List<OnDeviceProfileChangeListener> mDPChangeListeners = new ArrayList<>();

    public BaseTaskbarContext(Context windowContext) {
        super(windowContext, Themes.getActivityThemeRes(windowContext));
        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);
    }

    @Override
    public final LayoutInflater getLayoutInflater() {
        return mLayoutInflater;
    }

    @Override
    public final List<OnDeviceProfileChangeListener> getOnDeviceProfileChangeListeners() {
        return mDPChangeListeners;
    }

    /** Callback invoked when a drag is initiated within this context. */
    public abstract void onDragStart();

    /** Callback invoked when a drag is finished within this context. */
    public abstract void onDragEnd();

    /** Callback invoked when a popup is shown or closed within this context. */
    public abstract void onPopupVisibilityChanged(boolean isVisible);

    /**
     * Callback invoked when user attempts to split the screen through a long-press menu in Taskbar
     * or AllApps.
     */
    public abstract void onSplitScreenMenuButtonClicked();
}
