/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.bubbles.bar;

import android.annotation.ColorInt;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.wm.shell.R;
import com.android.wm.shell.shared.TypefaceUtils;

/**
 * Bubble bar expanded view menu item view to display menu action details
 */
public class BubbleBarMenuItemView extends LinearLayout {
    private ImageView mImageView;
    private TextView mTextView;

    public BubbleBarMenuItemView(Context context) {
        this(context, null /* attrs */);
    }

    public BubbleBarMenuItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0 /* defStyleAttr */);
    }

    public BubbleBarMenuItemView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0 /* defStyleRes */);
    }

    public BubbleBarMenuItemView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mImageView = findViewById(R.id.bubble_bar_menu_item_icon);
        mTextView = findViewById(R.id.bubble_bar_menu_item_title);
        TypefaceUtils.setTypeface(mTextView, TypefaceUtils.FontFamily.GSF_TITLE_MEDIUM);
    }

    /**
     * Update menu item with the details and tint color
     */
    void update(DrawableProvider icon, String title, @ColorInt int tint) {
        Drawable drawable = icon == null ? null : icon.getDrawable(getContext());
        if (tint == Color.TRANSPARENT) {
            mTextView.setTextColor(
                    getContext().getColor(com.android.internal.R.color.materialColorOnSurface));
        } else {
            if (drawable != null) {
                drawable.mutate();
                drawable.setTint(tint);
            }
            mTextView.setTextColor(tint);
        }

        mImageView.setImageDrawable(drawable);
        mTextView.setText(title);
    }
}
