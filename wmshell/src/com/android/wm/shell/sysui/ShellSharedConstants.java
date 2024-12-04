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

/**
 * General shell-related constants that are shared with users of the library.
 */
public class ShellSharedConstants {
    // See IPip.aidl
    public static final String KEY_EXTRA_SHELL_PIP = "extra_shell_pip";
    // See IBubbles.aidl
    public static final String KEY_EXTRA_SHELL_BUBBLES = "extra_shell_bubbles";
    // See ISplitScreen.aidl
    public static final String KEY_EXTRA_SHELL_SPLIT_SCREEN = "extra_shell_split_screen";
    // See IOneHanded.aidl
    public static final String KEY_EXTRA_SHELL_ONE_HANDED = "extra_shell_one_handed";
    // See IShellTransitions.aidl
    public static final String KEY_EXTRA_SHELL_SHELL_TRANSITIONS =
            "extra_shell_shell_transitions";
    // See IStartingWindow.aidl
    public static final String KEY_EXTRA_SHELL_STARTING_WINDOW =
            "extra_shell_starting_window";
    // See IRecentTasks.aidl
    public static final String KEY_EXTRA_SHELL_RECENT_TASKS = "extra_shell_recent_tasks";
    // See IBackAnimation.aidl
    public static final String KEY_EXTRA_SHELL_BACK_ANIMATION = "extra_shell_back_animation";
    // See IDesktopMode.aidl
    public static final String KEY_EXTRA_SHELL_DESKTOP_MODE = "extra_shell_desktop_mode";
    // See IDragAndDrop.aidl
    public static final String KEY_EXTRA_SHELL_DRAG_AND_DROP = "extra_shell_drag_and_drop";
    // See IRecentsAnimationController.aidl
    public static final String KEY_EXTRA_SHELL_CAN_HAND_OFF_ANIMATION =
            "extra_shell_can_hand_off_animation";
}
