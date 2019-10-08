/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep;

/**
 * Generic interface providing methods to the recents implementation that allow it to callback to
 * the containing activity.
 */
public interface RecentsToActivityHelper {

    /**
     * The default action to take when leaving/closing recents. In general, this should be used to
     * go to the appropriate home state.
     */
    void leaveRecents();
}
