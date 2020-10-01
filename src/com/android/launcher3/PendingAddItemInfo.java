/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.launcher3;

import android.content.ComponentName;

import androidx.annotation.Nullable;

import com.android.launcher3.model.data.ItemInfo;

import java.util.Optional;

/**
 * Meta data that is used for deferred binding. e.g., this object is used to pass information on
 * draggable targets when they are dropped onto the workspace from another container.
 */
public class PendingAddItemInfo extends ItemInfo {

    /**
     * The component that will be created.
     */
    public ComponentName componentName;

    @Override
    protected String dumpProperties() {
        return super.dumpProperties() + " componentName=" + componentName;
    }

    /**
     * Returns shallow copy of the object.
     */
    @Override
    public ItemInfo makeShallowCopy() {
        PendingAddItemInfo itemInfo = new PendingAddItemInfo();
        itemInfo.copyFrom(this);
        itemInfo.componentName = this.componentName;
        return itemInfo;
    }

    @Nullable
    @Override
    public ComponentName getTargetComponent() {
        return Optional.ofNullable(super.getTargetComponent()).orElse(componentName);
    }

}
