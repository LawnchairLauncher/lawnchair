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
import android.os.Handler
import android.view.Display
import android.view.SurfaceControl
import android.view.View.OnClickListener
import android.view.View.OnTouchListener
import android.window.WindowContainerTransaction
import com.android.app.tracing.traceSection
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.windowdecor.WindowDecorLinearLayout
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.viewholder.AppHandleViewHolder
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder

/**
 * Controller for the app handle. Creates, updates, and removes the views of the caption
 * and its menus.
 */
class AppHandleController(
    private val decorWindowContext: Context,
    private val onCaptionTouchListener: OnTouchListener,
    private val onCaptionButtonClickListener: OnClickListener,
    private val windowManagerWrapper: WindowManagerWrapper,
    @ShellMainThread private val handler: Handler,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val appHandleViewHolderFactory: AppHandleViewHolder.Factory =
        AppHandleViewHolder.Factory(),
) : CaptionController<WindowDecorLinearLayout>(windowDecorViewHostSupplier) {

    override val captionType = CaptionType.APP_HANDLE

    override fun relayout(
        params: RelayoutParams,
        parentContainer: SurfaceControl,
        display: Display,
        decorWindowContext: Context,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        wct: WindowContainerTransaction,
    ): CaptionRelayoutResult = traceSection("AppHandleController#relayout") {
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

    override fun createCaptionView(): WindowDecorationViewHolder<AppHandleViewHolder.HandleData> {
        val appHandleViewHolder = appHandleViewHolderFactory.create(
            // View holder should inflate the caption's root view
            rootView = null,
            context = decorWindowContext,
            onCaptionTouchListener = onCaptionTouchListener,
            onCaptionButtonClickListener = onCaptionButtonClickListener,
            windowManagerWrapper = windowManagerWrapper,
            handler = handler,
            desktopModeUiEventLogger = desktopModeUiEventLogger,
        )
        return appHandleViewHolder
    }

    override fun getCaptionHeight(captionPadding: Int): Int = 0
}
