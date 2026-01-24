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
 * Any object responsible to generate an id for a component.
 */
interface CompatUIComponentIdGenerator {

    /**
     * Generates the unique id for a component given a {@link CompatUIInfo} and component
     * {@link CompatUISpec}.
     * @param compatUIInfo  The object encapsulating information about the current Task.
     * @param spec  The {@link CompatUISpec} for the component.
     */
    fun generateId(compatUIInfo: CompatUIInfo, spec: CompatUISpec): String
}
