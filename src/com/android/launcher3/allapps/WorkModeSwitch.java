/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.RequiresApi;

import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.pm.UserCache;

/**
 * Work profile toggle switch shown at the bottom of AllApps work tab
 */
public class WorkModeSwitch extends Button implements Insettable, View.OnClickListener {

    private Rect mInsets = new Rect();
    private boolean mWorkEnabled;

    public WorkModeSwitch(Context context) {
        this(context, null, 0);
    }

    public WorkModeSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkModeSwitch(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOnClickListener(this);
    }

    @Override
    public void setInsets(Rect insets) {
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        ViewGroup.MarginLayoutParams marginLayoutParams =
                (ViewGroup.MarginLayoutParams) getLayoutParams();
        if (marginLayoutParams != null) {
            marginLayoutParams.bottomMargin = bottomInset + marginLayoutParams.bottomMargin;
        }
    }

    /**
     * Animates in/out work profile toggle panel based on the tab user is on
     */
    public void setWorkTabVisible(boolean workTabVisible) {
        clearAnimation();
        if (workTabVisible) {
            setEnabled(true);
            setVisibility(VISIBLE);
            setAlpha(0);
            animate().alpha(1).start();
        } else {
            animate().alpha(0).withEndAction(() -> this.setVisibility(GONE)).start();
        }
    }

    @Override
    public void onClick(View view) {
        if (Utilities.ATLEAST_P) {
            setEnabled(false);
            UI_HELPER_EXECUTOR.post(() -> setToState(!mWorkEnabled));
        }
    }

    /**
     * Sets the enabled or disabled state of the button
     */
    public void updateCurrentState(boolean active) {
        mWorkEnabled = active;
        setEnabled(true);
        setCompoundDrawablesRelativeWithIntrinsicBounds(
                active ? R.drawable.ic_corp_off : R.drawable.ic_corp, 0, 0, 0);
        setText(active ? R.string.work_apps_pause_btn_text : R.string.work_apps_enable_btn_text);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    protected Boolean setToState(boolean toState) {
        UserManager userManager = getContext().getSystemService(UserManager.class);
        boolean showConfirm = false;
        for (UserHandle userProfile : UserCache.INSTANCE.get(getContext()).getUserProfiles()) {
            if (Process.myUserHandle().equals(userProfile)) {
                continue;
            }
            showConfirm |= !userManager.requestQuietModeEnabled(!toState, userProfile);
        }
        return showConfirm;
    }
}
