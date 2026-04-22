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

package com.android.wm.shell.windowdecor.caption

import android.content.Context
import android.view.Display
import android.view.SurfaceControl
import android.view.View
import android.view.View.OnLongClickListener
import android.window.WindowContainerTransaction
import com.android.app.tracing.traceSection
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder

/**
 * Controller for the app header. Creates, updates, and removes the views of the caption
 * and its menus.
 */
class AppHeaderController(
    private val decorWindowContext: Context,
    private val windowDecorationActions: WindowDecorationActions,
    private val onCaptionTouchListener: View.OnTouchListener,
    private val onCaptionButtonClickListener: View.OnClickListener,
    private val onLongClickListener: OnLongClickListener,
    private val onCaptionGenericMotionListener: View.OnGenericMotionListener,
    private val onMaximizeHoverAnimationFinishedListener: () -> Unit,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val appHeaderViewHolderFactory: AppHeaderViewHolder.Factory =
        AppHeaderViewHolder.Factory(),
) : CaptionController<WindowDecorLinearLayout>(windowDecorViewHostSupplier) {

    override val captionType = CaptionType.APP_HEADER

    override fun relayout(
        params: RelayoutParams,
        parentContainer: SurfaceControl,
        display: Display,
        decorWindowContext: Context,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        wct: WindowContainerTransaction,
    ): CaptionRelayoutResult = traceSection("AppHeaderController#relayout") {
        return super.relayout(
            params,
            parentContainer,
            display,
            decorWindowContext,
            startT,
            finishT,
            wct
        )
    }

    override fun createCaptionView(): WindowDecorationViewHolder<AppHeaderViewHolder.HeaderData> {
        val appHeaderViewHolder = appHeaderViewHolderFactory.create(
            // View holder should inflate the caption's root view
            rootView = null,
            context = decorWindowContext,
            windowDecorationActions = windowDecorationActions,
            onCaptionTouchListener = onCaptionTouchListener,
            onCaptionButtonClickListener = onCaptionButtonClickListener,
            onLongClickListener = onLongClickListener,
            onCaptionGenericMotionListener = onCaptionGenericMotionListener,
            onMaximizeHoverAnimationFinishedListener = onMaximizeHoverAnimationFinishedListener,
            desktopModeUiEventLogger = desktopModeUiEventLogger,
        )
        return appHeaderViewHolder
    }

    override fun getCaptionHeight(captionPadding: Int): Int = 0
}
