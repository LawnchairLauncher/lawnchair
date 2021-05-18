/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.launcher3.taskbar;

import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_BACK;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_HOME;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_IME_SWITCH;
import static com.android.launcher3.taskbar.TaskbarNavButtonController.BUTTON_RECENTS;

import android.annotation.DrawableRes;
import android.view.View;
import android.widget.ImageView;

import com.android.launcher3.R;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarButton;

/**
 * Creates Buttons for Taskbar for 3 button nav.
 * Can add animations and state management for buttons in this class as things progress.
 */
public class ButtonProvider {

    private final int mMarginLeftRight;
    private final TaskbarActivityContext mContext;

    public ButtonProvider(TaskbarActivityContext context) {
        mContext = context;
        mMarginLeftRight = context.getResources()
                .getDimensionPixelSize(R.dimen.taskbar_icon_spacing);
    }

    public View getBack() {
        // Back button
        return getButtonForDrawable(R.drawable.ic_sysbar_back, BUTTON_BACK);
    }

    public View getDown() {
        // Ime down button
        return getButtonForDrawable(R.drawable.ic_sysbar_back, BUTTON_BACK);
    }

    public View getHome() {
        // Home button
        return getButtonForDrawable(R.drawable.ic_sysbar_home, BUTTON_HOME);
    }

    public View getRecents() {
        // Recents button
        return getButtonForDrawable(R.drawable.ic_sysbar_recent, BUTTON_RECENTS);
    }

    public View getImeSwitcher() {
        // IME Switcher Button
        return getButtonForDrawable(R.drawable.ic_ime_switcher, BUTTON_IME_SWITCH);
    }

    private View getButtonForDrawable(@DrawableRes int drawableId, @TaskbarButton int buttonType) {
        ImageView buttonView = new ImageView(mContext);
        buttonView.setImageResource(drawableId);
        buttonView.setBackgroundResource(R.drawable.taskbar_icon_click_feedback_roundrect);
        buttonView.setPadding(mMarginLeftRight, 0, mMarginLeftRight, 0);
        buttonView.setOnClickListener(view -> mContext.onNavigationButtonClick(buttonType));
        return buttonView;
    }

}
