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
package com.android.launcher3.taskbar.bubbles;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.launcher3.R;
import com.android.launcher3.icons.IconNormalizer;

// TODO: (b/276978250) This is will be similar to WMShell's BadgedImageView, it'd be nice to share.
// TODO: (b/269670235) currently this doesn't show the 'update dot'
/**
 * View that displays a bubble icon, along with an app badge on either the left or
 * right side of the view.
 */
public class BubbleView extends ConstraintLayout {

    // TODO: (b/269670235) currently we don't render the 'update dot', this will be used for that.
    public static final int DEFAULT_PATH_SIZE = 100;

    private final ImageView mBubbleIcon;
    private final ImageView mAppIcon;
    private final int mBubbleSize;

    // TODO: (b/273310265) handle RTL
    private boolean mOnLeft = false;

    private BubbleBarBubble mBubble;

    public BubbleView(Context context) {
        this(context, null);
    }

    public BubbleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BubbleView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // We manage positioning the badge ourselves
        setLayoutDirection(LAYOUT_DIRECTION_LTR);

        LayoutInflater.from(context).inflate(R.layout.bubble_view, this);

        mBubbleSize = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_size);
        mBubbleIcon = findViewById(R.id.icon_view);
        mAppIcon = findViewById(R.id.app_icon_view);

        setFocusable(true);
        setClickable(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                BubbleView.this.getOutline(outline);
            }
        });
    }

    private void getOutline(Outline outline) {
        final int normalizedSize = IconNormalizer.getNormalizedCircleSize(mBubbleSize);
        final int inset = (mBubbleSize - normalizedSize) / 2;
        outline.setOval(inset, inset, inset + normalizedSize, inset + normalizedSize);
    }

    /** Sets the bubble being rendered in this view. */
    void setBubble(BubbleBarBubble bubble) {
        mBubble = bubble;
        mBubbleIcon.setImageBitmap(bubble.getIcon());
        mAppIcon.setImageBitmap(bubble.getBadge());
    }

    /** Returns the bubble being rendered in this view. */
    @Nullable
    BubbleBarBubble getBubble() {
        return mBubble;
    }

    /** Shows the app badge on this bubble. */
    void showBadge() {
        Bitmap appBadgeBitmap = mBubble.getBadge();
        if (appBadgeBitmap == null) {
            mAppIcon.setVisibility(GONE);
            return;
        }

        int translationX;
        if (mOnLeft) {
            translationX = -(mBubble.getIcon().getWidth() - appBadgeBitmap.getWidth());
        } else {
            translationX = 0;
        }

        mAppIcon.setTranslationX(translationX);
        mAppIcon.setVisibility(VISIBLE);
    }

    /** Hides the app badge on this bubble. */
    void hideBadge() {
        mAppIcon.setVisibility(GONE);
    }

    @Override
    public String toString() {
        return "BubbleView{" + mBubble + "}";
    }
}
