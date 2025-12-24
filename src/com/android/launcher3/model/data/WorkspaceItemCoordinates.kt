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

package com.android.launcher3.model.data

/**
 * Represents the spacial coordinates of a single workspace item.
 *
 * @property screenId Specifies the screen on which the item's cell is located.
 * @property cellX Specifies the X position of the associated cell.
 * @property cellY Specifies the Y position of the associated cell.
 */
data class WorkspaceItemCoordinates(val screenId: Int, val cellX: Int, val cellY: Int)
