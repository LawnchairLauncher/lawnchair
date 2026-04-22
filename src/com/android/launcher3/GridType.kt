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

package com.android.launcher3

import androidx.annotation.IntDef

/** The type of grid. */
@IntDef(GridType.GRID_TYPE_ONE_GRID, GridType.GRID_TYPE_NON_ONE_GRID, GridType.GRID_TYPE_ANY)
@Retention(AnnotationRetention.SOURCE)
annotation class GridType {
    companion object {
        /** These are grids that use one grid spec. */
        const val GRID_TYPE_ONE_GRID = 1
        /** These are grids that don't use one grid spec. */
        const val GRID_TYPE_NON_ONE_GRID = 2
        /** Any grid type. */
        const val GRID_TYPE_ANY = GRID_TYPE_NON_ONE_GRID or GRID_TYPE_ONE_GRID
    }
}
