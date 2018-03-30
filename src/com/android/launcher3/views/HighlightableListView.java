/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HeaderViewListAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

import java.util.ArrayList;

/**
 * Extension of list view with support for element highlighting.
 */
public class HighlightableListView extends ListView {

    private int mPosHighlight = -1;
    private boolean mColorAnimated = false;

    public HighlightableListView(Context context) {
        super(context);
    }

    public HighlightableListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HighlightableListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(new HighLightAdapter(adapter));
    }

    public void highlightPosition(int pos) {
        if (mPosHighlight == pos) {
            return;
        }

        mColorAnimated = false;
        mPosHighlight = pos;
        setSelection(mPosHighlight);

        int start = getFirstVisiblePosition();
        int end = getLastVisiblePosition();
        if (start <= mPosHighlight && mPosHighlight <= end) {
            highlightView(getChildAt(mPosHighlight - start));
        }
    }

    private void highlightView(View view) {
        if (Boolean.TRUE.equals(view.getTag(R.id.view_highlighted))) {
            // already highlighted
        } else {
            view.setTag(R.id.view_highlighted, true);
            view.setTag(R.id.view_unhighlight_background, view.getBackground());
            view.setBackground(getHighlightBackground());
            view.postDelayed(() -> {
                mPosHighlight = -1;
                unhighlightView(view);
            }, 15000L);
        }
    }

    private void unhighlightView(View view) {
        if (Boolean.TRUE.equals(view.getTag(R.id.view_highlighted))) {
            Object background = view.getTag(R.id.view_unhighlight_background);
            if (background instanceof Drawable) {
                view.setBackground((Drawable) background);
            }
            view.setTag(R.id.view_unhighlight_background, null);
            view.setTag(R.id.view_highlighted, false);
        }
    }

    private class HighLightAdapter extends HeaderViewListAdapter {
        public HighLightAdapter(ListAdapter adapter) {
            super(new ArrayList<>(), new ArrayList<>(), adapter);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view =  super.getView(position, convertView, parent);

            if (position == mPosHighlight) {
                highlightView(view);
            } else {
                unhighlightView(view);
            }
            return view;
        }
    }

    private ColorDrawable getHighlightBackground() {
        int color = ColorUtils.setAlphaComponent(Themes.getColorAccent(getContext()), 26);
        if (mColorAnimated) {
            return new ColorDrawable(color);
        }
        mColorAnimated = true;
        ColorDrawable bg = new ColorDrawable(Color.WHITE);
        ObjectAnimator anim = ObjectAnimator.ofInt(bg, "color", Color.WHITE, color);
        anim.setEvaluator(new ArgbEvaluator());
        anim.setDuration(200L);
        anim.setRepeatMode(ValueAnimator.REVERSE);
        anim.setRepeatCount(4);
        anim.start();
        return bg;
    }
}
