/*
 * Copyright (C) 2010 The Android Open Source Project
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


package com.android.launcher2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Gallery;

public abstract class HomeCustomizationItemGallery extends Gallery
    implements Gallery.OnItemLongClickListener {

    protected Context mContext;

    protected Launcher mLauncher;

    protected int mMotionDownRawX;
    protected int mMotionDownRawY;

    public HomeCustomizationItemGallery(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLongClickable(true);
        setOnItemLongClickListener(this);
        mContext = context;

        setCallbackDuringFling(false);
    }

    public void setLauncher(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && mLauncher.isAllAppsVisible()) {
            return false;
        }

        super.onTouchEvent(ev);

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        switch (ev.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mMotionDownRawX = (int) ev.getRawX();
            mMotionDownRawY = (int) ev.getRawY();
        }
        return true;
    }
}

