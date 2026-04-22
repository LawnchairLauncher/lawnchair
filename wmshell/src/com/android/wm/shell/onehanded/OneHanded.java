/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.onehanded;

import android.os.SystemProperties;

import com.android.wm.shell.shared.annotations.ExternalThread;

/**
 * Interface to engage one handed feature.
 */
@ExternalThread
public interface OneHanded {

    boolean sIsSupportOneHandedMode =  SystemProperties.getBoolean(
            OneHandedController.SUPPORT_ONE_HANDED_MODE, false);

    /**
     * Enters one handed mode.
     */
    void startOneHanded();

    /**
     * Exits one handed mode.
     */
    void stopOneHanded();

    /**
     * Exits one handed mode with {@link OneHandedUiEventLogger}.
     */
    void stopOneHanded(int uiEvent);

    /**
     * Sets one handed feature temporary locked in enabled or disabled state, this won't change
     * settings configuration.
     *
     * @param locked locked function in disabled(can not trigger) or enabled state.
     * @param enabled function in disabled(can not trigger) or enabled state.
     */
    void setLockedDisabled(boolean locked, boolean enabled);

    /**
     * Registers callback to notify WMShell when user tap shortcut to expand notification.
     */
    void registerEventCallback(OneHandedEventCallback callback);

    /**
     * Registers callback to be notified after {@link OneHandedDisplayAreaOrganizer}
     * transition start or finish
     */
    void registerTransitionCallback(OneHandedTransitionCallback callback);
}
