/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.folder;

import com.android.launcher3.FolderInfo;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.config.FeatureFlags;

/**
 * Verifies whether an item in a Folder is displayed in the FolderIcon preview.
 */
public class FolderIconPreviewVerifier {

    public FolderIconPreviewVerifier(InvariantDeviceProfile profile) {
        // b/37570804
    }

    public void setFolderInfo(FolderInfo info) {
        // b/37570804
    }

    public boolean isItemInPreview(int rank) {
        return rank < FolderIcon.NUM_ITEMS_IN_PREVIEW;
    }
}
