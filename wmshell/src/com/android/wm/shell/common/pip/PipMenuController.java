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

package com.android.wm.shell.common.pip;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.FLAG_SLIPPERY;
import static android.view.WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
import static android.view.WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.RemoteAction;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.view.WindowManager;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;

import java.util.List;

/**
 *  Interface to interact with PiP menu when certain events happen
 *  (task appear/vanish, PiP move, etc.).
 */
public interface PipMenuController {

    String MENU_WINDOW_TITLE = "PipMenuView";

    /**
     * Used with
     * {@link PipMenuController#movePipMenu(SurfaceControl, SurfaceControl.Transaction, Rect,
     * float)} to indicate that we don't want to affect the alpha value of the menu surfaces.
     */
    float ALPHA_NO_CHANGE = -1f;

    /**
     * Called when out implementation of
     * {@link ShellTaskOrganizer.TaskListener#onTaskAppeared(RunningTaskInfo, SurfaceControl)}
     * is called.
     */
    void attach(SurfaceControl leash);

    /**
     * Called when our implementation of
     * {@link ShellTaskOrganizer.TaskListener#onTaskVanished(RunningTaskInfo)} is called.
     */
    void detach();

    /**
     * Check if menu is visible or not.
     */
    boolean isMenuVisible();

    /**
     * Show the PIP menu.
     */
    void showMenu();

    /**
     * Given a set of actions, update the menu.
     */
    void setAppActions(List<RemoteAction> appActions, RemoteAction closeAction);

    /**
     * Resize the PiP menu with the given bounds. The PiP SurfaceControl is given if there is a
     * need to synchronize the movements on the same frame as PiP.
     */
    default void resizePipMenu(@Nullable SurfaceControl pipLeash,
            @Nullable SurfaceControl.Transaction t,
            Rect destinationBounds) {}

    /**
     * Move the PiP menu with the given bounds. The PiP SurfaceControl is given if there is a
     * need to synchronize the movements on the same frame as PiP.
     */
    default void movePipMenu(@Nullable SurfaceControl pipLeash,
            @Nullable SurfaceControl.Transaction t, Rect destinationBounds, float alpha) {
    }

    /**
     * Update the PiP menu with the given bounds for re-layout purposes.
     */
    default void updateMenuBounds(Rect destinationBounds) {}

    /**
     * Returns a default LayoutParams for the PIP Menu.
     * @param context the context.
     * @param width the PIP stack width.
     * @param height the PIP stack height.
     */
    default WindowManager.LayoutParams getPipMenuLayoutParams(Context context, String title,
            int width, int height) {
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(width, height,
                TYPE_APPLICATION_OVERLAY,
                FLAG_WATCH_OUTSIDE_TOUCH | FLAG_SPLIT_TOUCH | FLAG_SLIPPERY | FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.privateFlags |= PRIVATE_FLAG_TRUSTED_OVERLAY;
        lp.setTitle(title);
        lp.accessibilityTitle = context.getResources().getString(
                R.string.pip_menu_accessibility_title);
        return lp;
    }
}
