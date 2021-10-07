/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.shared.system.smartspace;

import com.android.systemui.shared.system.smartspace.SmartspaceState;

// Methods for getting and setting the state of a SmartSpace. This is used to allow a remote process
// (such as System UI) to sync with and control a SmartSpace view hosted in another process (such as
// Launcher).
interface ISmartspaceCallback {

    // Return information about the state of the SmartSpace, including location on-screen and
    // currently selected page.
    SmartspaceState getSmartspaceState();

    // Set the currently selected page of this SmartSpace.
    oneway void setSelectedPage(int selectedPage);

    oneway void setVisibility(int visibility);
}