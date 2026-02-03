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

package com.android.wm.shell.compatui;

import android.annotation.IdRes;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.wm.shell.R;

/**
 * Container for compat UI controls.
 */
class CompatUILayout extends LinearLayout {

    private CompatUIWindowManager mWindowManager;

    public CompatUILayout(Context context) {
        this(context, null);
    }

    public CompatUILayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CompatUILayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CompatUILayout(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    void inject(CompatUIWindowManager windowManager) {
        mWindowManager = windowManager;
    }

    void setSizeCompatHintVisibility(boolean show) {
        setViewVisibility(R.id.size_compat_hint, show);
    }

    void setRestartButtonVisibility(boolean show) {
        setViewVisibility(R.id.size_compat_restart_button, show);
        // Hint should never be visible without button.
        if (!show) {
            setSizeCompatHintVisibility(/* show= */ false);
        }
    }

    private void setViewVisibility(@IdRes int resId, boolean show) {
        final View view = findViewById(resId);
        int visibility = show ? View.VISIBLE : View.GONE;
        if (view.getVisibility() == visibility) {
            return;
        }
        view.setVisibility(visibility);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        // Need to relayout after changes like hiding / showing a hint since they affect size.
        // Doing this directly in setSizeCompatHintVisibility can result in flaky animation.
        mWindowManager.relayout();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final ImageButton restartButton = findViewById(R.id.size_compat_restart_button);
        restartButton.setOnClickListener(view -> mWindowManager.onRestartButtonClicked());
        restartButton.setOnLongClickListener(view -> {
            mWindowManager.onRestartButtonLongClicked();
            return true;
        });

        final LinearLayout sizeCompatHint = findViewById(R.id.size_compat_hint);
        ((TextView) sizeCompatHint.findViewById(R.id.compat_mode_hint_text))
                .setText(R.string.restart_button_description);
        sizeCompatHint.setOnClickListener(view -> setSizeCompatHintVisibility(/* show= */ false));
    }
}
