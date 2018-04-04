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
package com.android.launcher3.util;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AbsListView.RecyclerListener;
import android.widget.ListView;

import com.android.launcher3.R;

/**
 * Utility class to scroll and highlight a list view item
 */
public class ListViewHighlighter implements OnScrollListener, RecyclerListener,
        OnLayoutChangeListener {

    private final ListView mListView;
    private int mPosHighlight;

    private boolean mColorAnimated = false;

    public ListViewHighlighter(ListView listView, int posHighlight) {
        mListView = listView;
        mPosHighlight = posHighlight;
        mListView.setOnScrollListener(this);
        mListView.setRecyclerListener(this);
        mListView.addOnLayoutChangeListener(this);

        mListView.post(this::tryHighlight);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        mListView.post(this::tryHighlight);
    }

    private void tryHighlight() {
        if (mPosHighlight < 0 || mListView.getChildCount() == 0) {
            return;
        }
        if (!highlightIfVisible(mListView.getFirstVisiblePosition(),
                mListView.getLastVisiblePosition())) {
            mListView.smoothScrollToPosition(mPosHighlight);
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView absListView, int i) { }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
            int visibleItemCount, int totalItemCount) {
        highlightIfVisible(firstVisibleItem, firstVisibleItem + visibleItemCount);
    }

    private boolean highlightIfVisible(int start, int end) {
        if (mPosHighlight < 0 || mListView.getChildCount() == 0) {
            return false;
        }
        if (start > mPosHighlight || mPosHighlight > end) {
            return false;
        }
        highlightView(mListView.getChildAt(mPosHighlight - start));

        // finish highlight
        mListView.setOnScrollListener(null);
        mListView.removeOnLayoutChangeListener(this);

        mPosHighlight = -1;
        return true;
    }

    @Override
    public void onMovedToScrapHeap(View view) {
        unhighlightView(view);
    }

    private void highlightView(View view) {
        if (Boolean.TRUE.equals(view.getTag(R.id.view_highlighted))) {
            // already highlighted
        } else {
            view.setTag(R.id.view_highlighted, true);
            view.setTag(R.id.view_unhighlight_background, view.getBackground());
            view.setBackground(getHighlightBackground());
            view.postDelayed(() -> {
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

    private ColorDrawable getHighlightBackground() {
        int color = ColorUtils.setAlphaComponent(Themes.getColorAccent(mListView.getContext()), 26);
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
