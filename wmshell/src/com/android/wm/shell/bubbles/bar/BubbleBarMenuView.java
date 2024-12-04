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
import android.graphics.drawable.Icon;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.Bubble;

import java.util.ArrayList;

/**
 * Bubble bar expanded view menu
 */
public class BubbleBarMenuView extends LinearLayout {
    private ViewGroup mBubbleSectionView;
    private ViewGroup mActionsSectionView;
    private ImageView mBubbleIconView;
    private TextView mBubbleTitleView;

    public BubbleBarMenuView(Context context) {
        this(context, null /* attrs */);
    }

    public BubbleBarMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0 /* defStyleAttr */);
    }

    public BubbleBarMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0 /* defStyleRes */);
    }

    public BubbleBarMenuView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBubbleSectionView = findViewById(R.id.bubble_bar_manage_menu_bubble_section);
        mActionsSectionView = findViewById(R.id.bubble_bar_manage_menu_actions_section);
        mBubbleIconView = findViewById(R.id.bubble_bar_manage_menu_bubble_icon);
        mBubbleTitleView = findViewById(R.id.bubble_bar_manage_menu_bubble_title);
    }

    /** Update menu details with bubble info */
    void updateInfo(Bubble bubble) {
        if (bubble.getIcon() != null) {
            mBubbleIconView.setImageIcon(bubble.getIcon());
        } else {
            mBubbleIconView.setImageBitmap(bubble.getBubbleIcon());
        }
        mBubbleTitleView.setText(bubble.getTitle());
    }

    /**
     * Update menu action items views
     * @param actions used to populate menu item views
     */
    void updateActions(ArrayList<MenuAction> actions) {
        mActionsSectionView.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mContext);

        for (MenuAction action : actions) {
            BubbleBarMenuItemView itemView = (BubbleBarMenuItemView) inflater.inflate(
                    R.layout.bubble_bar_menu_item, mActionsSectionView, false);
            itemView.update(action.mIcon, action.mTitle, action.mTint);
            itemView.setOnClickListener(action.mOnClick);
            mActionsSectionView.addView(itemView);
        }
    }

    /** Sets on close menu listener */
    void setOnCloseListener(Runnable onClose) {
        mBubbleSectionView.setOnClickListener(view -> {
            onClose.run();
        });
    }

    /**
     * Overridden to proxy to section views alpha.
     * @implNote
     * If animate alpha on the parent (menu container) view, section view shadows get distorted.
     * To prevent distortion and artifacts alpha changes applied directly on the section views.
     */
    @Override
    public void setAlpha(float alpha) {
        mBubbleSectionView.setAlpha(alpha);
        mActionsSectionView.setAlpha(alpha);
    }

    /**
     * Overridden to proxy section view alpha value.
     * @implNote
     * The assumption is that both section views have the same alpha value
     */
    @Override
    public float getAlpha() {
        return mBubbleSectionView.getAlpha();
    }

    /**
     * Menu action details used to create menu items
     */
    static class MenuAction {
        private Icon mIcon;
        private @ColorInt int mTint;
        private String mTitle;
        private OnClickListener mOnClick;

        MenuAction(Icon icon, String title, OnClickListener onClick) {
            this(icon, title, Color.TRANSPARENT, onClick);
        }

        MenuAction(Icon icon, String title, @ColorInt int tint, OnClickListener onClick) {
            this.mIcon = icon;
            this.mTitle = title;
            this.mTint = tint;
            this.mOnClick = onClick;
        }
    }
}
