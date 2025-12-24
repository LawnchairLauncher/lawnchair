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

package com.android.wm.shell.apptoweb

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Binder
import android.util.Slog
import android.view.IWindow
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.WindowManager.LayoutParams
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
import android.view.WindowlessWindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import android.window.TaskConstants
import com.android.window.flags2.Flags
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.compatui.DialogAnimationController
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum.DESKTOP_WINDOWING_APP_TO_WEB_CHANGE_OPEN_BY_DEFAULT_SETTINGS
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import java.util.function.Supplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


/**
 * Window manager for the open by default settings dialog
 */
internal class OpenByDefaultDialog(
    private val context: Context,
    private val userContext: Context,
    private val transitions: Transitions,
    private val taskInfo: RunningTaskInfo,
    private val taskSurface: SurfaceControl,
    private val displayController: DisplayController,
    private val taskResourceLoader: WindowDecorTaskResourceLoader,
    private val surfaceControlTransactionSupplier: Supplier<SurfaceControl.Transaction>,
    @ShellMainThread private val mainDispatcher: MainCoroutineDispatcher,
    @ShellMainThread private val mainScope: CoroutineScope,
    private val listener: DialogLifecycleListener,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
) {
    private lateinit var dialog: OpenByDefaultDialogView
    private lateinit var viewHost: SurfaceControlViewHost
    private lateinit var dialogWindowManager: DialogWindowManager
    private lateinit var appIconView: ImageView
    private lateinit var appNameView: TextView

    private lateinit var openInAppButton: RadioButton
    private lateinit var openInBrowserButton: RadioButton

    private val animationController =
        DialogAnimationController<OpenByDefaultDialogView>(context, "OpenByDefaultDialog")
    private val domainVerificationManager =
        userContext.getSystemService(DomainVerificationManager::class.java)!!
    private val packageName = taskInfo.baseActivity?.packageName!!
    private var linkHandlingAllowed: Boolean = false

    private var loadAppInfoJob: Job? = null

    init {
        createDialog()
        initializeRadioButtons()
        loadAppInfoJob = mainScope.launch {
            if (!isActive) return@launch
            val (name, icon) = taskResourceLoader.getNameAndHeaderIcon(taskInfo)
            bindAppInfo(icon, name)
        }
    }

    /** Creates an open by default settings dialog. */
    fun createDialog() {
        dialog = LayoutInflater.from(context)
            .inflate(
                R.layout.open_by_default_settings_dialog,
                null /* root */
            ) as OpenByDefaultDialogView
        appIconView = dialog.requireViewById(R.id.application_icon)
        appNameView = dialog.requireViewById(R.id.application_name)

        if (Flags.useInputReportedFocusForAccessibility()) {
            createDialogWindow()
        } else {
            // TODO: ag/34061541 - once landed, can refactor with simpler fix
            transitions.runOnIdle(this::createDialogWindow)
        }

        dialog.setDismissOnClickListener { closeMenu() }
        dialog.setConfirmButtonClickListener {
            setDefaultLinkHandlingSetting()
            // Log if user is confirming settings change
            if (isConfirmingSettingsChange()) {
                desktopModeUiEventLogger.log(
                    taskInfo,
                    DESKTOP_WINDOWING_APP_TO_WEB_CHANGE_OPEN_BY_DEFAULT_SETTINGS
                )
            }
            closeMenu()
        }

        listener.onDialogCreated()
        if (Flags.useInputReportedFocusForAccessibility()) {
            viewHost.requestInputFocus(true)
        }
    }

    private fun createDialogWindow() {
        val display = displayController.getDisplay(taskInfo.displayId)
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        val lp = LayoutParams(
            taskBounds.width(),
            taskBounds.height(),
            TYPE_APPLICATION_PANEL,
            FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            token = Binder()
            title = "Open by default settings dialog of task=${taskInfo.taskId}"
            setTrustedOverlay()
        }

        dialogWindowManager = DialogWindowManager(taskInfo.configuration)
        viewHost = SurfaceControlViewHost(context, display, dialogWindowManager, "Dialog").apply {
            setView(dialog, lp)
        }

        animationController.startEnterAnimation(dialog, this::onAnimationEnded)
    }

    private fun onAnimationEnded() {
        if (!Flags.useInputReportedFocusForAccessibility()) {
            dialog.post {
                dialog.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                val subHeader: TextView = dialog.requireViewById(R.id.dialog_subheader)
                subHeader.requestFocus()
                subHeader.requestAccessibilityFocus()
            }
        }
    }

    private fun initializeRadioButtons() {
        openInAppButton = dialog.requireViewById(R.id.open_in_app_button)
        openInBrowserButton = dialog.requireViewById(R.id.open_in_browser_button)

        val userState =
            getDomainVerificationUserState(domainVerificationManager, packageName) ?: return
        linkHandlingAllowed = userState.isLinkHandlingAllowed
        openInAppButton.isChecked = linkHandlingAllowed
        openInBrowserButton.isChecked = !linkHandlingAllowed
    }

    private fun isConfirmingSettingsChange() = if (linkHandlingAllowed) {
        openInBrowserButton.isChecked
    } else {
        openInAppButton.isChecked
    }

    private fun setDefaultLinkHandlingSetting() {
        try {
            domainVerificationManager.setDomainVerificationLinkHandlingAllowed(
                packageName, openInAppButton.isChecked)
        } catch (e: NameNotFoundException) {
            Slog.e(
                TAG,
                "Failed to change link handling policy due to the package name is not found: $e"
            )
        }
    }

    private fun closeMenu() {
        if (Flags.useInputReportedFocusForAccessibility()) {
            viewHost.requestInputFocus(false)
        }
        loadAppInfoJob?.cancel()
        animationController.startExitAnimation(dialog) {
            // Release the host and manager after the exit animation
            viewHost.release()
            dialogWindowManager.release()
            listener.onDialogDismissed()
        }
    }

     private fun bindAppInfo(appIconBitmap: Bitmap, appName: CharSequence) {
        appIconView.setImageBitmap(appIconBitmap)
        appNameView.text = appName
    }

    /**
     * Relayout the dialog to the new task bounds.
     */
    fun relayout(taskInfo: RunningTaskInfo) {
        val taskBounds = taskInfo.configuration.windowConfiguration.bounds
        dialogWindowManager.relayout(taskBounds)
        viewHost.relayout(taskBounds.width(), taskBounds.height())
    }

    /**
     * Dismiss dialog and set it to null, so it that it will be re-created on the next opening.
     */
    fun dismiss() = closeMenu()

    /**
     * Handles showing, positioning and tearing down the dialog surface
     */
    private inner class DialogWindowManager(config: Configuration) :
        WindowlessWindowManager(config, null, null) {

        private var leash: SurfaceControl? = null

        override fun getParentSurface(
            window: IWindow,
            attrs: LayoutParams
        ): SurfaceControl {
            val builder = SurfaceControl.Builder()
                .setContainerLayer()
                .setName("OpenByDefaultDialogLeash")
                .setParent(taskSurface)
                .setCallsite("OpenByDefaultDialog.getParentSurface")

            val newLeash = builder.build()
            leash = newLeash

            val t = surfaceControlTransactionSupplier.get()
            val taskBounds = taskInfo.configuration.windowConfiguration.bounds
            t.setPosition(newLeash, 0f, 0f)
                .setWindowCrop(newLeash, taskBounds.width(), taskBounds.height())
                .setLayer(newLeash, TaskConstants.TASK_CHILD_LAYER_SETTINGS_DIALOG)
                .show(newLeash)
                .apply()

            return newLeash
        }

        fun relayout(taskBounds: Rect) {
            leash?.let {
                surfaceControlTransactionSupplier.get()
                    .setWindowCrop(it, taskBounds.width(), taskBounds.height())
                    .apply()
            }
        }

        fun release() {
            leash?.let { surfaceControlTransactionSupplier.get().remove(it).apply() }
            leash = null
        }
    }

    /**
     * Defines interface for classes that can listen to lifecycle events of open by default settings
     * dialog.
     */
    interface DialogLifecycleListener {
        /** Called when open by default dialog view has been created. */
        fun onDialogCreated() {}

        /** Called when open by default dialog view has been released. */
        fun onDialogDismissed() {}
    }

    companion object {
        private const val TAG = "OpenByDefaultDialog"
    }
}
