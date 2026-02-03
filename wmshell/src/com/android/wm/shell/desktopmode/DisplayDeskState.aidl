/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.desktopmode;

/**
 * Defines the state of desks on a display whose ID is `displayId`, which is:
 * - `activeDeskId`: the currently active desk Id, or `-1` if none is active.
 * - `deskId`: the list of desk Ids of the available desks on this display.
 */
parcelable DisplayDeskState {
    int displayId;
    int activeDeskId;
    int[] deskIds;
}

