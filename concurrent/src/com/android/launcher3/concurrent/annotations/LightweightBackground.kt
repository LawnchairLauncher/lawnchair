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

package com.android.launcher3.concurrent.annotations

import javax.inject.Qualifier

/**
 * Priority of the lightweight background executor.
 */
enum class LightweightBackgroundPriority {

    /**
     * Priority definition for lightweight background work that is not time
     * sensitive, typically for data transformations.
     */
    DATA,

    /**
     * Priority definition for lightweight background work that is time
     * sensitive, typically for user interaction feedback.
     */
    UI
}

/**
 * Qualifier for an [Executor] or derivative that runs on a lightweight
 * background thread.
 *
 * This is a lightweight executor that is suitable for running cheap background
 * tasks that can either be time sensitive or not depending on the
 * [LightweightBackgroundPriority] flag.
 *
 * LighweightBackground is distinct from Background in that it will not pollute
 * the other as to ensure responsiveness of either.
 *
 * Non priority usecases include:
 * - Data transformations within repository layers
 *
 * Priority usecases include:
 * - Responses to user interactions
 *
 * Tasks submitted to this executor are guaranteed to be ordered.
 *
 * @param priority The priority of the lightweight background executor. By
 * default, this is [LightweightBackgroundPriority.DATA].
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LightweightBackground(
  val priority: LightweightBackgroundPriority = LightweightBackgroundPriority.DATA
)