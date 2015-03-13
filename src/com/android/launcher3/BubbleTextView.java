/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.TextView;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends TextView {

    private static SparseArray<Theme> sPreloaderThemes = new SparseArray<Theme>(2);

    private static final float SHADOW_LARGE_RADIUS = 4.0f;
    private static final float SHADOW_SMALL_RADIUS = 1.75f;
    private static final float SHADOW_Y_OFFSET = 2.0f;
    private static final int SHADOW_LARGE_COLOUR = 0xDD000000;
    private static final int SHADOW_SMALL_COLOUR = 0xCC000000;
    static final float PADDING_V = 3.0f;

    private Drawable mIcon;
    private final Drawable mBackground;
    private final CheckLongPressHelper mLongPressHelper;
    private final HolographicOutlineHelper mOutlineHelper;

    // TODO: Remove custom background handling code, as no instance of BubbleTextView use any
    // background.
    private boolean mBackgroundSizeChanged;

    private Bitmap mPressedBackground;

    private float mSlop;

    private final boolean mDeferShadowGenerationOnTouch;
    private final boolean mCustomShadowsEnabled;
    private final boolean mLayoutHorizontal;
    private final int mIconSize;
    private final int mIconPaddingSize;
    private final int mTextSize;
    private int mTextColor;

    private boolean mStayPressed;
    private boolean mIgnorePressedStateChange;

    public BubbleTextView(Context context) {
        this(context, null, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BubbleTextView, defStyle, 0);
        mCustomShadowsEnabled = a.getBoolean(R.styleable.BubbleTextView_customShadows, true);
        mLayoutHorizontal = a.getBoolean(R.styleable.BubbleTextView_layoutHorizontal, false);
        mIconSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconSizeOverride,
                grid.allAppsIconSizePx);
        mIconPaddingSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconPaddingOverride,
                grid.iconDrawablePaddingPx);
        mTextSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_textSizeOverride,
                grid.allAppsIconTextSizePx);
        mDeferShadowGenerationOnTouch =
                a.getBoolean(R.styleable.BubbleTextView_deferShadowGeneration, false);
        a.recycle();

        if (mCustomShadowsEnabled) {
            // Draw the background itself as the parent is drawn twice.
            mBackground = getBackground();
            setBackground(null);
        } else {
            mBackground = null;
        }

        // If we are laying out horizontal, then center the text vertically
        if (mLayoutHorizontal) {
            setGravity(Gravity.CENTER_VERTICAL);
        }

        mLongPressHelper = new CheckLongPressHelper(this);

        mOutlineHelper = HolographicOutlineHelper.obtain(getContext());
        if (mCustomShadowsEnabled) {
            setShadowLayer(SHADOW_LARGE_RADIUS, 0.0f, SHADOW_Y_OFFSET, SHADOW_LARGE_COLOUR);
        }

        setAccessibilityDelegate(LauncherAppState.getInstance().getAccessibilityDelegate());
    }

    public void onFinishInflate() {
        super.onFinishInflate();

        // Ensure we are using the right text size
        setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize);
    }

    public void applyFromShortcutInfo(ShortcutInfo info, IconCache iconCache,
            boolean setDefaultPadding) {
        applyFromShortcutInfo(info, iconCache, setDefaultPadding, false);
    }

    public void applyFromShortcutInfo(ShortcutInfo info, IconCache iconCache,
            boolean setDefaultPadding, boolean promiseStateChanged) {
        Bitmap b = info.getIcon(iconCache);

        FastBitmapDrawable iconDrawable = Utilities.createIconDrawable(b);
        iconDrawable.setGhostModeEnabled(info.isDisabled != 0);

        setIcon(iconDrawable, mIconSize, setDefaultPadding ? mIconPaddingSize : -1);
        if (info.contentDescription != null) {
            setContentDescription(info.contentDescription);
        }
        setText(info.title);
        setTag(info);

        if (promiseStateChanged || info.isPromise()) {
            applyState(promiseStateChanged);
        }
    }

    public void applyFromApplicationInfo(AppInfo info) {
        setIcon(Utilities.createIconDrawable(info.iconBitmap), mIconSize, mIconPaddingSize);
        setText(info.title);
        if (info.contentDescription != null) {
            setContentDescription(info.contentDescription);
        }
        setTag(info);
    }

    @Override
    protected boolean setFrame(int left, int top, int right, int bottom) {
        if (getLeft() != left || getRight() != right || getTop() != top || getBottom() != bottom) {
            mBackgroundSizeChanged = true;
        }
        return super.setFrame(left, top, right, bottom);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return who == mBackground || super.verifyDrawable(who);
    }

    @Override
    public void setTag(Object tag) {
        if (tag != null) {
            LauncherModel.checkItemInfo((ItemInfo) tag);
        }
        super.setTag(tag);
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);

        if (!mIgnorePressedStateChange) {
            updateIconState();
        }
    }

    /** Returns the icon for this view. */
    public Drawable getIcon() {
        return mIcon;
    }

    /** Returns whether the layout is horizontal. */
    public boolean isLayoutHorizontal() {
        return mLayoutHorizontal;
    }

    private void updateIconState() {
        if (mIcon instanceof FastBitmapDrawable) {
            ((FastBitmapDrawable) mIcon).setPressed(isPressed() || mStayPressed);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        boolean result = super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // So that the pressed outline is visible immediately on setStayPressed(),
                // we pre-create it on ACTION_DOWN (it takes a small but perceptible amount of time
                // to create it)
                if (!mDeferShadowGenerationOnTouch && mPressedBackground == null) {
                    mPressedBackground = mOutlineHelper.createMediumDropShadow(this);
                }

                mLongPressHelper.postCheckForLongPress();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // If we've touched down and up on an item, and it's still not "pressed", then
                // destroy the pressed outline
                if (!isPressed()) {
                    mPressedBackground = null;
                }

                mLongPressHelper.cancelLongPress();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!Utilities.pointInView(this, event.getX(), event.getY(), mSlop)) {
                    mLongPressHelper.cancelLongPress();
                }
                break;
        }
        return result;
    }

    void setStayPressed(boolean stayPressed) {
        mStayPressed = stayPressed;
        if (!stayPressed) {
            mPressedBackground = null;
        } else {
            if (mPressedBackground == null) {
                mPressedBackground = mOutlineHelper.createMediumDropShadow(this);
            }
        }

        // Only show the shadow effect when persistent pressed state is set.
        if (getParent() instanceof ShortcutAndWidgetContainer) {
            CellLayout layout = (CellLayout) getParent().getParent();
            layout.setPressedIcon(this, mPressedBackground, mOutlineHelper.shadowBitmapPadding);
        }

        updateIconState();
    }

    void clearPressedBackground() {
        setPressed(false);
        setStayPressed(false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (super.onKeyDown(keyCode, event)) {
            // Pre-create shadow so show immediately on click.
            if (mPressedBackground == null) {
                mPressedBackground = mOutlineHelper.createMediumDropShadow(this);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Unlike touch events, keypress event propagate pressed state change immediately,
        // without waiting for onClickHandler to execute. Disable pressed state changes here
        // to avoid flickering.
        mIgnorePressedStateChange = true;
        boolean result = super.onKeyUp(keyCode, event);

        mPressedBackground = null;
        mIgnorePressedStateChange = false;
        updateIconState();
        return result;
    }

    @Override
    public void draw(Canvas canvas) {
        if (!mCustomShadowsEnabled) {
            super.draw(canvas);
            return;
        }

        final Drawable background = mBackground;
        if (background != null) {
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();

            if (mBackgroundSizeChanged) {
                background.setBounds(0, 0,  getRight() - getLeft(), getBottom() - getTop());
                mBackgroundSizeChanged = false;
            }

            if ((scrollX | scrollY) == 0) {
                background.draw(canvas);
            } else {
                canvas.translate(scrollX, scrollY);
                background.draw(canvas);
                canvas.translate(-scrollX, -scrollY);
            }
        }

        // If text is transparent, don't draw any shadow
        if (getCurrentTextColor() == getResources().getColor(android.R.color.transparent)) {
            getPaint().clearShadowLayer();
            super.draw(canvas);
            return;
        }

        // We enhance the shadow by drawing the shadow twice
        getPaint().setShadowLayer(SHADOW_LARGE_RADIUS, 0.0f, SHADOW_Y_OFFSET, SHADOW_LARGE_COLOUR);
        super.draw(canvas);
        canvas.save(Canvas.CLIP_SAVE_FLAG);
        canvas.clipRect(getScrollX(), getScrollY() + getExtendedPaddingTop(),
                getScrollX() + getWidth(),
                getScrollY() + getHeight(), Region.Op.INTERSECT);
        getPaint().setShadowLayer(SHADOW_SMALL_RADIUS, 0.0f, 0.0f, SHADOW_SMALL_COLOUR);
        super.draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mBackground != null) mBackground.setCallback(this);

        if (mIcon instanceof PreloadIconDrawable) {
            ((PreloadIconDrawable) mIcon).applyPreloaderTheme(getPreloaderTheme());
        }
        mSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBackground != null) mBackground.setCallback(null);
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        super.setTextColor(color);
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        mTextColor = colors.getDefaultColor();
        super.setTextColor(colors);
    }

    public void setTextVisibility(boolean visible) {
        Resources res = getResources();
        if (visible) {
            super.setTextColor(mTextColor);
        } else {
            super.setTextColor(res.getColor(android.R.color.transparent));
        }
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        return true;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        mLongPressHelper.cancelLongPress();
    }

    public void applyState(boolean promiseStateChanged) {
        if (getTag() instanceof ShortcutInfo) {
            ShortcutInfo info = (ShortcutInfo) getTag();
            final boolean isPromise = info.isPromise();
            final int progressLevel = isPromise ?
                    ((info.hasStatusFlag(ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE) ?
                            info.getInstallProgress() : 0)) : 100;

            if (mIcon != null) {
                final PreloadIconDrawable preloadDrawable;
                if (mIcon instanceof PreloadIconDrawable) {
                    preloadDrawable = (PreloadIconDrawable) mIcon;
                } else {
                    preloadDrawable = new PreloadIconDrawable(mIcon, getPreloaderTheme());
                    setIcon(preloadDrawable, mIconSize, -1);
                }

                preloadDrawable.setLevel(progressLevel);
                if (promiseStateChanged) {
                    preloadDrawable.maybePerformFinishedAnimation();
                }
            }
        }
    }

    private Theme getPreloaderTheme() {
        Object tag = getTag();
        int style = ((tag != null) && (tag instanceof ShortcutInfo) &&
                (((ShortcutInfo) tag).container >= 0)) ? R.style.PreloadIcon_Folder
                        : R.style.PreloadIcon;
        Theme theme = sPreloaderThemes.get(style);
        if (theme == null) {
            theme = getResources().newTheme();
            theme.applyStyle(style, true);
            sPreloaderThemes.put(style, theme);
        }
        return theme;
    }

    /**
     * Sets the icon for this view based on the layout direction.
     */
    private Drawable setIcon(Drawable icon, int iconSize, int drawablePadding) {
        mIcon = icon;
        if (iconSize != -1) {
            mIcon.setBounds(0, 0, iconSize, iconSize);
        }
        if (mLayoutHorizontal) {
            setCompoundDrawablesRelative(mIcon, null, null, null);
        } else {
            setCompoundDrawablesRelative(null, mIcon, null, null);
        }
        if (drawablePadding != -1) {
            setCompoundDrawablePadding(drawablePadding);
        }
        return icon;
    }
}
