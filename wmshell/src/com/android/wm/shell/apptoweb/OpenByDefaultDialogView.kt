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
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.window.flags2.Flags
import com.android.wm.shell.R
import com.android.wm.shell.compatui.DialogContainerSupplier

/** View for open by default settings dialog for an application which allows the user to change
 * where links will open by default, in the default browser or in the application. */
class OpenByDefaultDialogView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), DialogContainerSupplier {

    private lateinit var dialogContainer: View
    private lateinit var dialogTitle: TextView
    private lateinit var dialogSubheader: TextView
    private lateinit var openInAppButton: RadioButton
    private lateinit var openInBrowserButton: RadioButton
    private lateinit var dismissButton: Button
    private lateinit var backgroundDim: Drawable

    fun setDismissOnClickListener(callback: (View) -> Unit) {
        // Clicks on the background dim should also dismiss the dialog.
        setOnClickListener(callback)
        // We add a no-op on-click listener to the dialog container so that clicks on it won't
        // propagate to the listener of the layout (which represents the background dim).
        dialogContainer.setOnClickListener { }
    }

    fun setConfirmButtonClickListener(callback: (View) -> Unit) {
        dismissButton.setOnClickListener(callback)
    }

    override fun getDialogContainerView(): View = dialogContainer

    override fun getBackgroundDimDrawable(): Drawable = backgroundDim

    override fun onFinishInflate() {
        super.onFinishInflate()
        accessibilityPaneTitle = context.getString(R.string.open_by_default_settings_text)
        dialogContainer = requireViewById(R.id.open_by_default_dialog_container)
        dialogTitle = dialogContainer.requireViewById(R.id.application_name)
        dialogSubheader = dialogContainer.requireViewById(R.id.dialog_subheader)
        openInAppButton = dialogContainer.requireViewById(R.id.open_in_app_button)
        openInBrowserButton = dialogContainer.requireViewById(R.id.open_in_browser_button)
        dismissButton = dialogContainer.requireViewById(
            R.id.open_by_default_settings_dialog_confirm_button
        )

        backgroundDim = background.mutate()
        backgroundDim.alpha = 128

        if (!Flags.useInputReportedFocusForAccessibility()) {
            setupA11yTraversal()
        }
    }

    // Set up a11y focus so that focus loops through elements within the dialog, instead of going to
    // elements behind the dialog.
    // TODO: ag/34061541 - once landed, see if we can refactor with simpler fix
    private fun setupA11yTraversal() {
        dialogTitle.accessibilityTraversalBefore = R.id.open_by_default_settings_dialog_confirm_button
        dialogTitle.accessibilityTraversalAfter = R.id.dialog_subheader

        dialogSubheader.accessibilityTraversalBefore = R.id.application_name
        dialogSubheader.accessibilityTraversalAfter = R.id.open_in_app_button

        openInAppButton.accessibilityTraversalBefore = R.id.dialog_subheader
        openInAppButton.accessibilityTraversalAfter = R.id.open_in_browser_button

        openInBrowserButton.accessibilityTraversalBefore = R.id.open_in_app_button
        openInBrowserButton.accessibilityTraversalAfter =
            R.id.open_by_default_settings_dialog_confirm_button

        dismissButton.accessibilityTraversalBefore = R.id.open_in_browser_button
        dismissButton.accessibilityTraversalAfter = R.id.application_name
    }
}
