/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher2;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotate fields of a subclass of {@link IntAllocaiton} or
 * FloatAllocation with this, and the save() method on those
 * those classes will find the field an save it.
 * <p>
 * TODO: This would be even better if the allocations were
 * named, and renderscript automatically added them into to
 * the renderscript namespace.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface AllocationIndex {
    /**
     * The index in the allocation to use inside renderscript.
     */
    int value();
}
