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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import com.android.launcher3.views.ActivityContext;

public class ImeBarView extends RelativeLayout {

    private ButtonProvider mButtonProvider;
    private View mImeView;

    public ImeBarView(Context context) {
        this(context, null);
    }

    public ImeBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImeBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void init(ButtonProvider buttonProvider) {
        mButtonProvider = buttonProvider;

        ActivityContext context = getActivityContext();
        RelativeLayout.LayoutParams imeParams = new RelativeLayout.LayoutParams(
                context.getDeviceProfile().iconSizePx,
                context.getDeviceProfile().iconSizePx
        );
        RelativeLayout.LayoutParams downParams = new RelativeLayout.LayoutParams(imeParams);

        imeParams.addRule(ALIGN_PARENT_END);
        imeParams.setMarginEnd(context.getDeviceProfile().iconSizePx);
        downParams.setMarginStart(context.getDeviceProfile().iconSizePx);
        downParams.addRule(ALIGN_PARENT_START);

        // Down Arrow
        View downView = mButtonProvider.getDown();
        downView.setLayoutParams(downParams);
        downView.setRotation(-90);
        addView(downView);

        // IME switcher button
        mImeView = mButtonProvider.getImeSwitcher();
        mImeView.setLayoutParams(imeParams);
        addView(mImeView);
    }

    public void setImeSwitcherVisibility(boolean show) {
        mImeView.setVisibility(show ? VISIBLE : GONE);
    }

    private <T extends Context & ActivityContext> T getActivityContext() {
        return ActivityContext.lookupContext(getContext());
    }
}
