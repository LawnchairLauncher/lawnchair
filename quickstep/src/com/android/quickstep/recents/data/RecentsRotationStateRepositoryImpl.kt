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

package com.android.quickstep.recents.data

import com.android.quickstep.util.RecentsOrientedState

/**
 * Repository for [RecentsRotationState] which holds orientation/rotation related information
 * related to Recents
 */
class RecentsRotationStateRepositoryImpl(private val state: RecentsOrientedState) :
    RecentsRotationStateRepository {
    override fun getRecentsRotationState() =
        with(state) {
            RecentsRotationState(
                activityRotation = recentsActivityRotation,
                orientationHandlerRotation = orientationHandler.rotation
            )
        }
}
