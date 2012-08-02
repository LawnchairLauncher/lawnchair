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
import android.view.KeyEvent;
import android.widget.TextView;

/**
 * We use a custom tab view to process our own focus traversals.
 */
public class AccessibleTabView extends TextView {
    public AccessibleTabView(Context context) {
        super(context);
    }

    public AccessibleTabView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AccessibleTabView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return FocusHelper.handleTabKeyEvent(this, keyCode, event)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return FocusHelper.handleTabKeyEvent(this, keyCode, event)
                || super.onKeyUp(keyCode, event);
    }
}
