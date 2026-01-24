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

import android.content.Context
import android.graphics.Point
import android.view.View
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup

/**
 * Defines the predicates to invoke for understanding if a component can be created or destroyed.
 */
class CompatUILifecyclePredicates(
    // Predicate evaluating to true if the component needs to be created
    val creationPredicate: (CompatUIInfo, CompatUISharedState) -> Boolean,
    // Predicate evaluating to true if the component needs to be destroyed
    val removalPredicate: (
        CompatUIInfo,
        CompatUISharedState,
        CompatUIComponentState?
    ) -> Boolean,
    // Builder for the initial state of the component
    val stateBuilder: (
        CompatUIInfo,
        CompatUISharedState
    ) -> CompatUIComponentState? = { _, _ -> null }
)

/**
 * Layout configuration
 */
data class CompatUILayout(
    val zOrder: Int = 0,
    val layoutParamFlags: Int = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL,
    val viewBuilder: (Context, CompatUIInfo, CompatUIComponentState?) -> View,
    val viewBinder: (
        View,
        CompatUIInfo,
        CompatUISharedState,
        CompatUIComponentState?
    ) -> Unit = { _, _, _, _ -> },
    val positionFactory: (
        View,
        CompatUIInfo,
        CompatUISharedState,
        CompatUIComponentState?
    ) -> Point,
    val viewReleaser: () -> Unit = {}
)

/**
 * Describes each compat ui component to the framework.
 */
class CompatUISpec(
    val log: (String) -> Unit = { str -> ProtoLog.v(ShellProtoLogGroup.WM_SHELL_COMPAT_UI, str) },
    // Unique name for the component. It's used for debug and for generating the
    // unique component identifier in the system.
    val name: String,
    // The lifecycle definition
    val lifecycle: CompatUILifecyclePredicates,
    // The layout definition
    val layout: CompatUILayout
)
