/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.compatui;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.wm.shell.R;

import java.util.function.Consumer;

/**
 * Container for a SCM restart confirmation dialog and background dim.
 */
public class RestartDialogLayout extends ConstraintLayout implements DialogContainerSupplier {

    private View mDialogContainer;
    private TextView mDialogTitle;
    private Drawable mBackgroundDim;

    public RestartDialogLayout(Context context) {
        this(context, null);
    }

    public RestartDialogLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RestartDialogLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public RestartDialogLayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public View getDialogContainerView() {
        return mDialogContainer;
    }

    TextView getDialogTitle() {
        return mDialogTitle;
    }

    @Override
    public Drawable getBackgroundDimDrawable() {
        return mBackgroundDim;
    }

    /**
     * Register a callback for the dismiss button and background dim.
     *
     * @param callback The callback to register or null if all on click listeners should be removed.
     */
    void setDismissOnClickListener(@Nullable Runnable callback) {
        final OnClickListener listener = callback == null ? null : view -> callback.run();
        findViewById(R.id.letterbox_restart_dialog_dismiss_button).setOnClickListener(listener);
    }

    /**
     * Register a callback for the restart button
     *
     * @param callback The callback to register or null if all on click listeners should be removed.
     */
    void setRestartOnClickListener(@Nullable Consumer<Boolean> callback) {
        final CheckBox dontShowAgainCheckbox = findViewById(R.id.letterbox_restart_dialog_checkbox);
        final OnClickListener listener = callback == null ? null : view -> callback.accept(
                dontShowAgainCheckbox.isChecked());
        findViewById(R.id.letterbox_restart_dialog_restart_button).setOnClickListener(listener);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final View checkboxContainer = findViewById(
                R.id.letterbox_restart_dialog_checkbox_container);
        final CheckBox checkbox = findViewById(R.id.letterbox_restart_dialog_checkbox);
        mDialogContainer = findViewById(R.id.letterbox_restart_dialog_container);
        mDialogTitle = findViewById(R.id.letterbox_restart_dialog_title);
        mBackgroundDim = getBackground().mutate();
        // Set the alpha of the background dim to 0 for enter animation.
        mBackgroundDim.setAlpha(0);
        // We add a no-op on-click listener to the dialog container so that clicks on it won't
        // propagate to the listener of the layout (which represents the background dim).
        mDialogContainer.setOnClickListener(view -> {});
        checkboxContainer.setOnClickListener(view -> checkbox.performClick());
    }
}
