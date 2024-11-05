/*
 * Copyright (C) 2024 The Android Open Source Project
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

/**
 * This interface should be implemented for any container/view that has a CellLayout as a children.
 */
public interface CellLayoutContainer {

    /**
     * Get the CellLayoutId for the given cellLayout.
     */
    int getCellLayoutId(CellLayout cellLayout);

    /**
     * Get the index of the given CellLayout out of all the other CellLayouts.
     */
    int getCellLayoutIndex(CellLayout cellLayout);

    /**
     * The total number of CellLayouts in the container.
     */
    int getPanelCount();

    /**
     * Used for accessibility, it returns the string that the assistant is going to say when
     * referring to the given CellLayout.
     */
    String getPageDescription(int pageIndex);
}
