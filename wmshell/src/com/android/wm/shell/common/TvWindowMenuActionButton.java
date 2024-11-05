/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.android.wm.shell.R;

/**
 * A common action button for TV window menu layouts.
 */
public class TvWindowMenuActionButton extends RelativeLayout {
    private final ImageView mIconImageView;
    private final View mButtonBackgroundView;

    private Icon mCurrentIcon;

    public TvWindowMenuActionButton(Context context) {
        this(context, null, 0, 0);
    }

    public TvWindowMenuActionButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0, 0);
    }

    public TvWindowMenuActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TvWindowMenuActionButton(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final LayoutInflater inflater = (LayoutInflater) getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.tv_window_menu_action_button, this);

        mIconImageView = findViewById(R.id.icon);
        mButtonBackgroundView = findViewById(R.id.background);

        final int[] values = new int[]{android.R.attr.src, android.R.attr.text};
        final TypedArray typedArray = context.obtainStyledAttributes(attrs, values, defStyleAttr,
                defStyleRes);

        setImageResource(typedArray.getResourceId(0, 0));
        final int textResId = typedArray.getResourceId(1, 0);
        if (textResId != 0) {
            setTextAndDescription(textResId);
        }
        typedArray.recycle();

        setIsCustomCloseAction(false);
    }

    /**
     * Sets the drawable for the button with the given drawable.
     */
    public void setImageDrawable(Drawable d) {
        mIconImageView.setImageDrawable(d);
    }

    /**
     * Sets the drawable for the button with the given resource id.
     */
    public void setImageResource(int resId) {
        if (resId != 0) {
            mIconImageView.setImageResource(resId);
        }
    }

    public void setImageIconAsync(Icon icon, Handler handler) {
        mCurrentIcon = icon;
        // Remove old image while waiting for the new one to load.
        mIconImageView.setImageDrawable(null);
        if (icon.getType() == Icon.TYPE_URI || icon.getType() == Icon.TYPE_URI_ADAPTIVE_BITMAP) {
            // Disallow loading icon from content URI
            return;
        }
        icon.loadDrawableAsync(mContext, d -> {
            // The image hasn't been set any other way and the drawable belongs to the most
            // recently set Icon.
            if (mIconImageView.getDrawable() == null && mCurrentIcon == icon) {
                mIconImageView.setImageDrawable(d);
            }
        }, handler);
    }

    /**
     * Sets the text for description the with the given string.
     */
    public void setTextAndDescription(CharSequence text) {
        setContentDescription(text);
    }

    /**
     * Sets the text and description with the given string resource id.
     */
    public void setTextAndDescription(int resId) {
        setTextAndDescription(getContext().getString(resId));
    }

    /**
     * Marks this button as a custom close action button.
     * This changes the style of the action button to highlight that this action finishes the
     * Picture-in-Picture activity.
     *
     * @param isCustomCloseAction sets or unsets this button as a custom close action button.
     */
    public void setIsCustomCloseAction(boolean isCustomCloseAction) {
        mIconImageView.setImageTintList(
                getResources().getColorStateList(
                        isCustomCloseAction ? R.color.tv_window_menu_close_icon
                                : R.color.tv_window_menu_icon));
        mButtonBackgroundView.setBackgroundTintList(getResources()
                .getColorStateList(isCustomCloseAction ? R.color.tv_window_menu_close_icon_bg
                        : R.color.tv_window_menu_icon_bg));
    }

    @Override
    public String toString() {
        if (getContentDescription() == null) {
            return TvWindowMenuActionButton.class.getSimpleName();
        }
        return getContentDescription().toString();
    }

}
