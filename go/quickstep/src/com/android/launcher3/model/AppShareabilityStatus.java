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
package com.android.launcher3.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.android.launcher3.model.AppShareabilityManager.ShareabilityStatus;

/**
 * Database entry to hold the shareability status of a single app
 */
@Entity
public class AppShareabilityStatus {
    @PrimaryKey
    @NonNull
    public String packageName;

    public @ShareabilityStatus int status;

    public AppShareabilityStatus(@NonNull String packageName, @ShareabilityStatus int status) {
        this.packageName = packageName;
        this.status = status;
    }
}
