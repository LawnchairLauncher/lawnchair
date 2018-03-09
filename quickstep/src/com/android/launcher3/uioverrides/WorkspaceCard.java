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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.NORMAL;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.launcher3.Launcher;
import com.android.quickstep.RecentsView.PageCallbacks;

public class WorkspaceCard extends View implements PageCallbacks, OnClickListener {

    private Launcher mLauncher;

    public WorkspaceCard(Context context) {
        this(context, null);
    }

    public WorkspaceCard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkspaceCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnClickListener(this);
    }

    /**
     * Draw nothing.
     */
    @Override
    public void draw(Canvas canvas) { }

    @Override
    public void onClick(View view) {
        mLauncher.getStateManager().goToState(NORMAL);
    }

    public void setup(Launcher launcher) {
        mLauncher = launcher;
    }
}
