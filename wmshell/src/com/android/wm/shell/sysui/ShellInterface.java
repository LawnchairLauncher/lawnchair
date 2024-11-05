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

package com.android.wm.shell.sysui;

import android.content.Context;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * General interface for notifying the Shell of common SysUI events like configuration or keyguard
 * changes.
 */
public interface ShellInterface {

    /**
     * Initializes the shell state.
     */
    default void onInit() {}

    /**
     * Notifies the Shell that the configuration has changed.
     */
    default void onConfigurationChanged(Configuration newConfiguration) {}

    /**
     * Notifies the Shell that the keyguard is showing (and if so, whether it is occluded) or not
     * showing, and whether it is animating a dismiss.
     */
    default void onKeyguardVisibilityChanged(boolean visible, boolean occluded,
            boolean animatingDismiss) {}

    /**
     * Notifies the Shell when the keyguard dismiss animation has finished.
     */
    default void onKeyguardDismissAnimationFinished() {}

    /**
     * Notifies the Shell when the user changes.
     */
    default void onUserChanged(int newUserId, @NonNull Context userContext) {}

    /**
     * Notifies the Shell when a profile belonging to the user changes.
     */
    default void onUserProfilesChanged(@NonNull List<UserInfo> profiles) {}

    /**
     * Registers a DisplayImeChangeListener to monitor for changes on Ime
     * position and visibility.
     */
    default void addDisplayImeChangeListener(DisplayImeChangeListener listener,
            Executor executor) {}

    /**
     * Removes a registered DisplayImeChangeListener.
     */
    default void removeDisplayImeChangeListener(DisplayImeChangeListener listener) {}

    /**
     * Handles a shell command.
     */
    default boolean handleCommand(final String[] args, PrintWriter pw) {
        return false;
    }

    /**
     * Updates the given {@param bundle} with the set of exposed interfaces.
     */
    default void createExternalInterfaces(Bundle bundle) {}

    /**
     * Dumps the shell state.
     */
    default void dump(PrintWriter pw) {}
}
