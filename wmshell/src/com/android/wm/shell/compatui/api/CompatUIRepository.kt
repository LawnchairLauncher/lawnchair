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

package com.android.wm.shell.compatui.api

/**
 * Abstraction for the repository of all the available CompatUISpec
 */
interface CompatUIRepository {
    /**
     * Adds a {@link CompatUISpec} to the repository
     * @throws IllegalStateException in case of illegal spec
     */
    fun addSpec(spec: CompatUISpec)

    /**
     * Iterates on the list of available {@link CompatUISpec} invoking
     * fn for each of them.
     */
    fun iterateOn(fn: (CompatUISpec) -> Unit)

    /**
     * Returns the {@link CompatUISpec} for a given key
     */
    fun findSpec(name: String): CompatUISpec?
}