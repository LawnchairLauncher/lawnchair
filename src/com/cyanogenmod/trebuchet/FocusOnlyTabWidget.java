/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.cyanogenmod.trebuchet;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TabWidget;

public class FocusOnlyTabWidget extends TabWidget {
    public FocusOnlyTabWidget(Context context) {
        super(context);
    }

    public FocusOnlyTabWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FocusOnlyTabWidget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public View getSelectedTab() {
        final int count = getTabCount();
        for (int i = 0; i < count; ++i) {
            View v = getChildTabViewAt(i);
            if (v.isSelected()) {
                return v;
            }
        }
        return null;
    }

    public int getChildTabIndex(View v) {
        final int tabCount = getTabCount();
        for (int i = 0; i < tabCount; ++i) {
            if (getChildTabViewAt(i) == v) {
                return i;
            }
        }
        return -1;
    }

    public void setCurrentTabToFocusedTab() {
        View tab = null;
        int index = -1;
        final int count = getTabCount();
        for (int i = 0; i < count; ++i) {
            View v = getChildTabViewAt(i);
            if (v.hasFocus()) {
                tab = v;
                index = i;
                break;
            }
        }
        if (index > -1) {
            super.setCurrentTab(index);
            super.onFocusChange(tab, true);
        }
    }

    @Override
    public void onFocusChange(android.view.View v, boolean hasFocus) {
        if (v == this && hasFocus && getTabCount() > 0) {
            getSelectedTab().requestFocus();
        }
    }
}
