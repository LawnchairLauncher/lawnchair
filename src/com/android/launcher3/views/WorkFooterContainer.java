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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * Container to show work footer in all-apps.
 */
public class WorkFooterContainer extends RelativeLayout {

    public WorkFooterContainer(Context context) {
        super(context);
    }

    public WorkFooterContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WorkFooterContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateTranslation();
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
}
