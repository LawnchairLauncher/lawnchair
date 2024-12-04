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

package com.android.wm.shell.bubbles;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.view.WindowManager;

import com.android.wm.shell.R;

public class TestableBubblePositioner extends BubblePositioner {
    private int mMaxBubbles;

    public TestableBubblePositioner(Context context,
            WindowManager windowManager) {
        super(context, windowManager);

        updateInternal(Configuration.ORIENTATION_PORTRAIT,
                Insets.of(0, 0, 0, 0),
                new Rect(0, 0, 500, 1000));
        mMaxBubbles = context.getResources().getInteger(R.integer.bubbles_max_rendered);
    }

    public void setMaxBubbles(int max) {
        mMaxBubbles = max;
    }

    @Override
    public int getMaxBubbles() {
        return mMaxBubbles;
    }
}
