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
package com.android.launcher3.shadows;

import android.app.backup.BackupManager;
import android.os.UserHandle;
import android.util.LongSparseArray;

import androidx.annotation.Nullable;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowBackupManager;

/**
 * Extension of {@link ShadowBackupManager} with missing shadow methods
 */
@Implements(value = BackupManager.class)
public class LShadowBackupManager extends ShadowBackupManager {

    private LongSparseArray<UserHandle> mProfileMapping = new LongSparseArray<>();

    public void addProfile(long userSerial, UserHandle userHandle) {
        mProfileMapping.put(userSerial, userHandle);
    }

    @Implementation
    @Nullable
    public UserHandle getUserForAncestralSerialNumber(long ancestralSerialNumber) {
        return mProfileMapping.get(ancestralSerialNumber);
    }
}
