/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.draganddrop;

/** Constants that can be used by both Shell and other users of the library, e.g. Launcher */
public class DragAndDropConstants {

    /**
     * An Intent extra that Launcher can use to specify a region of the screen where Shell should
     * ignore drag events.
     */
    public static final String EXTRA_DISALLOW_HIT_REGION = "DISALLOW_HIT_REGION";
}
