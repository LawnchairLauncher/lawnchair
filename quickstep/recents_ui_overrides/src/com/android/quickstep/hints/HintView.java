/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.hints;

import static com.android.quickstep.hints.HintUtil.getIcon;
import static com.android.quickstep.hints.HintUtil.getText;

import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.R;

public class HintView extends LinearLayout {
    private ImageView mIconView;
    private TextView mLabelView;

    public HintView(Context context) {
        super(context);
    }

    public HintView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public HintView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public HintView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setHint(Bundle hint) {
        mLabelView.setText(getText(hint));

        Icon icon = getIcon(hint);
        if (icon == null) {
            mIconView.setVisibility(GONE);
        } else {
            mIconView.setImageIcon(icon);
            mIconView.setVisibility(VISIBLE);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.icon);
        mLabelView = findViewById(R.id.label);
    }
}
