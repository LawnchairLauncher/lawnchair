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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TURN_OFF_WORK_APPS_TAP;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.KeyboardInsetAnimationCallback;
import com.android.launcher3.pm.UserCache;

/**
 * Work profile toggle switch shown at the bottom of AllApps work tab
 */
public class WorkModeSwitch extends Button implements Insettable, View.OnClickListener {

    private Rect mInsets = new Rect();
    private boolean mWorkEnabled;


    @Nullable
    private KeyboardInsetAnimationCallback mKeyboardInsetAnimationCallback;

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
        setSelected(true);
        setOnClickListener(this);
        if (Utilities.ATLEAST_R) {
            mKeyboardInsetAnimationCallback = new KeyboardInsetAnimationCallback(this);
            setWindowInsetsAnimationCallback(mKeyboardInsetAnimationCallback);
        }
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
            if (mWorkEnabled) {
                setVisibility(VISIBLE);
            }
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
            Launcher.fromContext(getContext()).getStatsLogManager().logger().log(
                    LAUNCHER_TURN_OFF_WORK_APPS_TAP);
            UI_HELPER_EXECUTOR.post(() -> setWorkProfileEnabled(getContext(), false));
        }
    }

    /**
     * Sets the enabled or disabled state of the button
     */
    public void updateCurrentState(boolean active) {
        mWorkEnabled = active;
        setEnabled(true);
        setVisibility(active ? VISIBLE : GONE);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    public static Boolean setWorkProfileEnabled(Context context, boolean enabled) {
        UserManager userManager = context.getSystemService(UserManager.class);
        boolean showConfirm = false;
        for (UserHandle userProfile : UserCache.INSTANCE.get(context).getUserProfiles()) {
            if (Process.myUserHandle().equals(userProfile)) {
                continue;
            }
            showConfirm |= !userManager.requestQuietModeEnabled(!enabled, userProfile);
        }
        return showConfirm;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (Utilities.ATLEAST_R) {
            setTranslationY(0);
            if (insets.isVisible(WindowInsets.Type.ime())) {
                Insets keyboardInsets = insets.getInsets(WindowInsets.Type.ime());
                setTranslationY(mInsets.bottom - keyboardInsets.bottom);
            }
        }
        return insets;
    }
}
