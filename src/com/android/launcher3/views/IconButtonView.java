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
package com.android.launcher3.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.FastBitmapDrawableDelegate;
import com.android.launcher3.icons.FastBitmapDrawableDelegate.DelegateFactory;
import com.android.launcher3.icons.IconShape;
import com.android.launcher3.util.MultiTranslateDelegate;

/**
 * Button in Taskbar that shows a tinted background and foreground.
 */
public class IconButtonView extends BubbleTextView {

    private static final int[] ATTRS = {android.R.attr.icon};

    private static final int INDEX_TASKBAR_ALL_APPS_ICON = MultiTranslateDelegate.COUNT;
    private static final int MY_COUNT = MultiTranslateDelegate.COUNT + 1;

    private final MultiTranslateDelegate mTranslateDelegate =
            new MultiTranslateDelegate(this, MY_COUNT, MultiTranslateDelegate.COUNT);

    public IconButtonView(Context context) {
        this(context, null);
    }

    public IconButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IconButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, ATTRS, defStyle, 0);
        Drawable fg = a.getDrawable(0);
        a.recycle();

        ColorStateList tintList = getBackgroundTintList();
        int tint = tintList == null ? Color.WHITE : tintList.getDefaultColor();

        if (fg == null) {
            fg = new ColorDrawable(Color.TRANSPARENT);
        }
        setIcon(new FastBitmapDrawable(
                BitmapInfo.LOW_RES_INFO,
                IconShape.EMPTY,
                new IconDelegateFactory(tint, fg)));
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        event.getText().add(this.getContentDescription());
    }

    /** Sets given Drawable as icon */
    public void setIconDrawable(@NonNull Drawable drawable) {
        ColorStateList tintList = getBackgroundTintList();
        int tint = tintList == null ? Color.WHITE : tintList.getDefaultColor();
        setIcon(new FastBitmapDrawable(
                BitmapInfo.LOW_RES_INFO,
                IconShape.EMPTY,
                new IconDelegateFactory(tint, drawable)));
    }

    /** Updates the color of the icon's foreground layer. */
    public void setForegroundTint(@ColorInt int tintColor) {
        FastBitmapDrawable icon = getIcon();
        if (icon != null && (icon.getDelegate() instanceof IconDelegate delegate)) {
            delegate.mFg.setTint(tintColor);
        }
    }

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    /**
     * Sets translationX for taskbar all apps icon
     */
    public void setTranslationXForTaskbarAllAppsIcon(float translationX) {
        getTranslateDelegate().getTranslationX(INDEX_TASKBAR_ALL_APPS_ICON).setValue(translationX);
    }

    private static class IconDelegate implements FastBitmapDrawableDelegate {

        private final Drawable mFg;

        IconDelegate(Paint paint, int colorBg, Drawable fg) {
            paint.setColorFilter(new BlendModeColorFilter(colorBg, BlendMode.SRC_IN));
            mFg = fg;
        }

        @Override
        public void drawContent(@NonNull BitmapInfo info,
                @NonNull IconShape shape,
                @NonNull Canvas canvas, @NonNull Rect bounds, @NonNull Paint paint) {
            mFg.draw(canvas);
        }

        @Override
        public void onBoundsChange(@NonNull Rect bounds) {
            mFg.setBounds(bounds);
        }
    }

    private record IconDelegateFactory(int colorBg, Drawable fg) implements DelegateFactory {

        @NonNull
        @Override
        public FastBitmapDrawableDelegate newDelegate(@NonNull BitmapInfo bitmapInfo,
                @NonNull IconShape iconShape, @NonNull Paint paint,
                @NonNull FastBitmapDrawable host) {
            return new IconDelegate(paint, colorBg, fg);
        }
    }
}
