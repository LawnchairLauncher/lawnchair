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

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Callbacks for when the user or user's profiles changes.
 */
public interface UserChangeListener {
    /**
     * Called when the current (parent) user changes.
     */
    default void onUserChanged(int newUserId, @NonNull Context userContext) {}

    /**
     * Called when a profile belonging to the user changes.
     */
    default void onUserProfilesChanged(@NonNull List<UserInfo> profiles) {}
}
