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

@file:JvmName("CommonConstants")

package com.android.wm.shell.flicker.utils

import android.tools.traces.component.ComponentNameMatcher

const val SYSTEM_UI_PACKAGE_NAME = "com.android.systemui"
const val LAUNCHER_UI_PACKAGE_NAME = "com.google.android.apps.nexuslauncher"
val APP_PAIR_SPLIT_DIVIDER_COMPONENT = ComponentNameMatcher("", "AppPairSplitDivider#")
val DOCKED_STACK_DIVIDER_COMPONENT = ComponentNameMatcher("", "DockedStackDivider#")
val SPLIT_SCREEN_DIVIDER_COMPONENT = ComponentNameMatcher("", "StageCoordinatorSplitDivider#")
val SPLIT_DECOR_MANAGER = ComponentNameMatcher("", "SplitDecorManager#")

enum class Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT
}
