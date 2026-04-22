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

package com.android.wm.shell.compatui.impl

import android.content.Context
import android.graphics.Point
import android.view.View
import com.android.wm.shell.compatui.api.CompatUIComponentState
import com.android.wm.shell.compatui.api.CompatUIInfo
import com.android.wm.shell.compatui.api.CompatUILayout
import com.android.wm.shell.compatui.api.CompatUISharedState
import junit.framework.Assert.assertEquals

/**
 * Fake class for {@link CompatUILayout}
 */
class FakeCompatUILayout(
    private val zOrderReturn: Int = 0,
    private val layoutParamFlagsReturn: Int = 0,
    private val viewBuilderReturn: View,
    private val positionBuilderReturn: Point = Point(0, 0)
) {

    var viewBuilderInvocation = 0
    var viewBinderInvocation = 0
    var positionFactoryInvocation = 0
    var viewReleaserInvocation = 0

    var lastViewBuilderContext: Context? = null
    var lastViewBuilderCompatUIInfo: CompatUIInfo? = null
    var lastViewBuilderCompState: CompatUIComponentState? = null
    var lastViewBinderView: View? = null
    var lastViewBinderCompatUIInfo: CompatUIInfo? = null
    var lastViewBinderSharedState: CompatUISharedState? = null
    var lastViewBinderCompState: CompatUIComponentState? = null
    var lastPositionFactoryView: View? = null
    var lastPositionFactoryCompatUIInfo: CompatUIInfo? = null
    var lastPositionFactorySharedState: CompatUISharedState? = null
    var lastPositionFactoryCompState: CompatUIComponentState? = null

    fun getLayout() = CompatUILayout(
        zOrder = zOrderReturn,
        layoutParamFlags = layoutParamFlagsReturn,
        viewBuilder = { ctx, info, componentState ->
            lastViewBuilderContext = ctx
            lastViewBuilderCompatUIInfo = info
            lastViewBuilderCompState = componentState
            viewBuilderInvocation++
            viewBuilderReturn
        },
        viewBinder = { view, info, sharedState, componentState ->
            lastViewBinderView = view
            lastViewBinderCompatUIInfo = info
            lastViewBinderCompState = componentState
            lastViewBinderSharedState = sharedState
            viewBinderInvocation++
        },
        positionFactory = { view, info, sharedState, componentState ->
            lastPositionFactoryView = view
            lastPositionFactoryCompatUIInfo = info
            lastPositionFactoryCompState = componentState
            lastPositionFactorySharedState = sharedState
            positionFactoryInvocation++
            positionBuilderReturn
        },
        viewReleaser = { viewReleaserInvocation++ }
    )

    fun assertViewBuilderInvocation(expected: Int) =
        assertEquals(expected, viewBuilderInvocation)

    fun assertViewBinderInvocation(expected: Int) =
        assertEquals(expected, viewBinderInvocation)

    fun assertViewReleaserInvocation(expected: Int) =
        assertEquals(expected, viewReleaserInvocation)

    fun assertPositionFactoryInvocation(expected: Int) =
        assertEquals(expected, positionFactoryInvocation)

    fun resetState() {
        viewBuilderInvocation = 0
        viewBinderInvocation = 0
        positionFactoryInvocation = 0
        viewReleaserInvocation = 0
        lastViewBuilderCompatUIInfo = null
        lastViewBuilderCompState = null
        lastViewBinderView = null
        lastViewBinderCompatUIInfo = null
        lastViewBinderSharedState = null
        lastViewBinderCompState = null
        lastPositionFactoryView = null
        lastPositionFactoryCompatUIInfo = null
        lastPositionFactorySharedState = null
        lastPositionFactoryCompState = null
    }
}
