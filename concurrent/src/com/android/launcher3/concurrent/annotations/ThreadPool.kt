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
 * Qualifier for an [Executor] or derivative that runs on a background thread
 * pool.
 *
 * This is a background executor that provides execution of tasks that
 * are not time sensitive and are time consuming.
 *
 * Usecases include:
 * - Multiple disk I/O interactions
 * - Network I/O interactions
 * - etc.
 *
 * Tasks submitted to this executor are not guaranteed to be ordered and may be
 * executed immediately or at a later time.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ThreadPool