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
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.wm.shell.R

/** View for open by default settings dialog for an application which allows the user to change
 * where links will open by default, in the default browser or in the application. */
class OpenByDefaultDialogView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes) {

    private lateinit var dialogContainer: View
    private lateinit var backgroundDim: Drawable

    fun setDismissOnClickListener(callback: (View) -> Unit) {
        // Clicks on the background dim should also dismiss the dialog.
        setOnClickListener(callback)
        // We add a no-op on-click listener to the dialog container so that clicks on it won't
        // propagate to the listener of the layout (which represents the background dim).
        dialogContainer.setOnClickListener { }
    }

    fun setConfirmButtonClickListener(callback: (View) -> Unit) {
        val dismissButton = dialogContainer.requireViewById<Button>(
            R.id.open_by_default_settings_dialog_confirm_button
        )
        dismissButton.setOnClickListener(callback)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        dialogContainer = requireViewById(R.id.open_by_default_dialog_container)
        backgroundDim = background.mutate()
        backgroundDim.alpha = 128
    }
}
