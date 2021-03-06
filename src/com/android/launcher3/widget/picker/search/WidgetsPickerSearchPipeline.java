/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.launcher3.widget.picker.search;

import com.android.launcher3.widget.model.WidgetsListBaseEntry;

import java.util.List;
import java.util.function.Consumer;

/**
 * An interface for a pipeline to handle widgets search.
 */
public interface WidgetsPickerSearchPipeline {

    /**
     * Performs a search query asynchronically. Invokes {@code callback} when the search is
     * complete.
     */
    void query(String input, Consumer<List<WidgetsListBaseEntry>> callback);

    /**
     * Cancels any ongoing search request.
     */
    default void cancel() {};

    /**
     * Cleans up after search is no longer needed.
     */
    default void destroy() {};
}
