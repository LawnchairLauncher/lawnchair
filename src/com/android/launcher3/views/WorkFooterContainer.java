/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.views;

import static com.android.launcher3.util.PackageManagerHelper.hasShortcutsPermission;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.WorkModeSwitch;
import com.android.launcher3.pm.UserCache;

/**
 * Container to show work footer in all-apps.
 */
public class WorkFooterContainer extends LinearLayout implements Insettable {
    private Rect mInsets = new Rect();

    private WorkModeSwitch mWorkModeSwitch;
    private TextView mWorkModeLabel;

    protected final ObjectAnimator mOpenCloseAnimator;

    public WorkFooterContainer(Context context) {
        this(context, null, 0);
    }

    public WorkFooterContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkFooterContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mOpenCloseAnimator = ObjectAnimator.ofPropertyValuesHolder(this);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateTranslation();
        this.setVisibility(shouldShowWorkFooter() ? VISIBLE : GONE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mWorkModeSwitch = findViewById(R.id.work_mode_toggle);
        mWorkModeLabel = findViewById(R.id.work_mode_label);
    }

    @Override
    public void offsetTopAndBottom(int offset) {
        super.offsetTopAndBottom(offset);
        updateTranslation();
    }

    private void updateTranslation() {
        if (getParent() instanceof View) {
            View parent = (View) getParent();
            int availableBot = parent.getHeight() - parent.getPaddingBottom();
            setTranslationY(Math.max(0, availableBot - getBottom()));
        }
    }

    @Override
    public void setInsets(Rect insets) {
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(),
                getPaddingBottom() + bottomInset);
    }

    /**
     * Animates in/out work profile toggle panel based on the tab user is on
     */
    public void setWorkTabVisible(boolean workTabVisible) {
        if (!shouldShowWorkFooter()) return;

        mOpenCloseAnimator.setValues(PropertyValuesHolder.ofFloat(ALPHA, workTabVisible ? 1 : 0));
        mOpenCloseAnimator.start();
    }

    /**
     * Refreshes views based on current work profile enabled status
     */
    public void refresh() {
        if (!shouldShowWorkFooter()) return;
        boolean anyProfileQuietModeEnabled = UserCache.INSTANCE.get(
                getContext()).isAnyProfileQuietModeEnabled();

        mWorkModeLabel.setText(anyProfileQuietModeEnabled
                ? R.string.work_mode_off_label : R.string.work_mode_on_label);
        mWorkModeLabel.setCompoundDrawablesWithIntrinsicBounds(
                anyProfileQuietModeEnabled ? R.drawable.ic_corp_off : R.drawable.ic_corp, 0, 0, 0);
        mWorkModeSwitch.refresh();
    }

    private boolean shouldShowWorkFooter() {
        Launcher launcher = Launcher.getLauncher(getContext());
        return Utilities.ATLEAST_P && (hasShortcutsPermission(launcher)
                || launcher.checkSelfPermission("android.permission.MODIFY_QUIET_MODE")
                == PackageManager.PERMISSION_GRANTED);
    }
}
