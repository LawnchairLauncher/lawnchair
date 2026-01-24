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
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.graphics.Point;
import android.os.UserHandle;
import android.view.LayoutInflater;

import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.BaseContext;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.Themes;
import com.android.quickstep.SystemUiProxy;

// TODO(b/218912746): Share more behavior to avoid all apps context depending directly on taskbar.
/** Base for common behavior between taskbar window contexts. */
public abstract class BaseTaskbarContext extends BaseContext
        implements SystemShortcut.BubbleActivityStarter {

    private final int mDisplayId;
    private final boolean mIsPrimaryDisplay;
    protected final LayoutInflater mLayoutInflater;

    public BaseTaskbarContext(Context windowContext, int displayId, boolean isPrimaryDisplay) {
        super(windowContext, Themes.getActivityThemeRes(windowContext));
        mDisplayId = displayId;
        mIsPrimaryDisplay = isPrimaryDisplay;
        mLayoutInflater = LayoutInflater.from(this).cloneInContext(this);
    }

    @Override
    public int getDisplayId() {
        return mDisplayId;
    }

    /**
     * Returns whether the taskbar is displayed on primary or external display.
     */
    public final boolean isPrimaryDisplay() {
        return mIsPrimaryDisplay;
    }

    /**
     * Returns whether taskbar is transient or persistent. External displays will be persistent.
     *
     * @return {@code true} if transient, {@code false} if persistent.
     */
    public abstract boolean isTransientTaskbar();

    /**
     * Returns whether the taskbar is pinned in gesture navigation mode.
     */
    public abstract boolean isPinnedTaskbar();

    /**
     * Returns the current navigation mode. External displays will be in THREE_BUTTONS mode.
     */
    public abstract NavigationMode getNavigationMode();

    /**
     * Returns whether the taskbar is in desktop mode. Implies that some desktop tasks are currently
     * visible.
     */
    public abstract boolean isInDesktopMode();

    /**
     * Returns whether the taskbar is showing desktop tasks, which may happen even outside desktop
     * mode on freeform displays.
     */
    public abstract boolean isTaskbarShowingDesktopTasks();

    /**
     * Returns whether the taskbar is forced to be pinned when home is visible.
     */
    public abstract  boolean showLockedTaskbarOnHome();

    /**
     * Returns whether desktop taskbar (pinned taskbar that shows desktop tasks) is to be used on
     * the display because the display is a freeform display.
     */
    public abstract  boolean showDesktopTaskbarForFreeformDisplay();

    /**
     * Returns screen size.
     */
    public abstract Point getScreenSize();

    /**
     * Returns display height.
     */
    public abstract int getDisplayHeight();

    /**
     * Notifies the context that the configuration has changed.
     */
    public abstract void notifyConfigChanged();


    @Override
    public final LayoutInflater getLayoutInflater() {
        return mLayoutInflater;
    }

    @Override
    public void showShortcutBubble(ShortcutInfo info) {
        if (info == null) return;
        SystemUiProxy.INSTANCE.get(this).showShortcutBubble(info);
    }

    @Override
    public void showAppBubble(Intent intent, UserHandle user) {
        if (intent == null || intent.getPackage() == null) return;
        SystemUiProxy.INSTANCE.get(this).showAppBubble(intent, user);
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
