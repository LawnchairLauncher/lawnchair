/*
 * Copyright (C) 2008 The Android Open Source Project
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


package com.android.launcher;

import android.widget.ImageView;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.KeyEvent;

public class HandleView extends ImageView {
    private static final int ORIENTATION_HORIZONTAL = 1;

    private Launcher mLauncher;
    private int mOrientation = ORIENTATION_HORIZONTAL;

    public HandleView(Context context) {
        super(context);
    }

    public HandleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HandleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.HandleView, defStyle, 0);
        mOrientation = a.getInt(R.styleable.HandleView_direction, ORIENTATION_HORIZONTAL);
        a.recycle();
    }

    @Override
    public View focusSearch(int direction) {
        View newFocus = super.focusSearch(direction);
        if (newFocus == null && mLauncher.isDrawerDown()) {
            final Workspace workspace = mLauncher.getWorkspace();
            workspace.dispatchUnhandledMove(null, direction);
            return (mOrientation == ORIENTATION_HORIZONTAL && direction == FOCUS_DOWN) ?
                    this : workspace;
        }
        return newFocus;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final boolean handled = super.onKeyDown(keyCode, event);

        if (!handled && !mLauncher.isDrawerDown() && !isDirectionKey(keyCode)) {
            return mLauncher.getApplicationsGrid().onKeyDown(keyCode, event);
        }

        return handled;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        final boolean handled = super.onKeyUp(keyCode, event);

        if (!handled && !mLauncher.isDrawerDown() && !isDirectionKey(keyCode)) {
            return mLauncher.getApplicationsGrid().onKeyUp(keyCode, event);
        }

        return handled;
    }

    private static boolean isDirectionKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_UP;
    }

    void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }
}
