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

package com.android.launcher3.widgetpicker.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Adds a test tag to the modifier.
 *
 * The test tag is prefixed with the widget picker package name to avoid conflicts with other
 * packages. This tag enables using `By.res(..)` to find UI elements in UI automator tests. See
 * https://developer.android.com/develop/ui/compose/testing/interoperability#uiautomator-interop
 *
 * For unit testing, prefer content description, text or other content based selectors.
 */
@Stable
fun Modifier.widgetPickerTestTag(id: String): Modifier {
  return this.semantics { testTag = buildWidgetPickerTestTag(id) }
}

/**
 * Mark this node as a container that contains one or more [widgetPickerTestTag] descendants.
 *
 * Should be used on the top level composable of a widget picker screen.
 * @see [widgetPickerTestTag] for more details.
 */
@Stable
fun Modifier.widgetPickerTestTagContainer(): Modifier {
  return this.then(Modifier.semantics { testTagsAsResourceId = true })
}

/** Builds a test tag prefixed with the widget picker package name. */
fun buildWidgetPickerTestTag(id: String): String = "com.android.launcher3.widgetpicker:id/$id"
